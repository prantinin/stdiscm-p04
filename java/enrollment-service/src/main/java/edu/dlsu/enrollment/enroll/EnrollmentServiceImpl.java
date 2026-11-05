package edu.dlsu.enrollment.enroll;

import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.devsecurity.AuthUser;
import edu.dlsu.enrollment.devsecurity.DevAuthInterceptor;
import enrollment.enroll.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.sql.*;

public final class EnrollmentServiceImpl extends EnrollmentServiceGrpc.EnrollmentServiceImplBase {
  private final HikariDataSource dataSource;
  public EnrollmentServiceImpl(HikariDataSource dataSource) { this.dataSource = dataSource; }

  @Override public void enrollStudent(EnrollRequest request, StreamObserver<EnrollResponse> observer) {
    try {
      AuthUser user = student();
      if (request.getCourseCode().isBlank() || request.getSection().isBlank()) throw Status.INVALID_ARGUMENT.withDescription("Course code and section are required").asRuntimeException();
      observer.onNext(EnrollResponse.newBuilder().setRecord(enroll(user.username(), request.getCourseCode().trim(), request.getSection().trim())).build()); observer.onCompleted();
    } catch (StatusRuntimeException e) { observer.onError(e); }
  }

  @Override public void listMyEnrollments(ListMyEnrollmentsRequest request, StreamObserver<ListMyEnrollmentsResponse> observer) {
    try {
      AuthUser user = student();
      String sql = "SELECT e.id,c.code,c.section,c.name,c.schedule,e.enrolled_at FROM enrollments e JOIN courses c ON c.id=e.course_id WHERE e.student_username=? ORDER BY e.enrolled_at DESC,c.code,c.section";
      ListMyEnrollmentsResponse.Builder result = ListMyEnrollmentsResponse.newBuilder();
      try (Connection conn=dataSource.getConnection(); PreparedStatement ps=conn.prepareStatement(sql)) {
        ps.setString(1,user.username()); try (ResultSet rs=ps.executeQuery()) { while(rs.next()) result.addRecords(record(rs)); }
      } catch (SQLException e) { throw Status.INTERNAL.withDescription("Unable to list enrollments").withCause(e).asRuntimeException(); }
      observer.onNext(result.build()); observer.onCompleted();
    } catch (StatusRuntimeException e) { observer.onError(e); }
  }

  private EnrollmentRecord enroll(String student, String code, String section) {
    String find = "SELECT id,code,section,name,schedule,capacity,status FROM courses WHERE code=? AND section=? FOR UPDATE";
    try (Connection conn=dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try (PreparedStatement course=conn.prepareStatement(find)) {
        course.setString(1,code); course.setString(2,section);
        try (ResultSet rs=course.executeQuery()) {
          if (!rs.next()) throw Status.NOT_FOUND.withDescription("Course not found").asRuntimeException();
          long courseId=rs.getLong("id");
          if (alreadyEnrolled(conn, student, courseId))
            throw Status.ALREADY_EXISTS.withDescription("You are already enrolled in this course").asRuntimeException();
          if (!"OPEN".equals(rs.getString("status")) || countEnrollments(conn,courseId) >= rs.getInt("capacity"))
            throw Status.FAILED_PRECONDITION.withDescription("Course is full or closed").asRuntimeException();
          long id;
          try (PreparedStatement insert=conn.prepareStatement("INSERT INTO enrollments(student_username,course_id) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1,student); insert.setLong(2,courseId); insert.executeUpdate();
            try(ResultSet keys=insert.getGeneratedKeys()) { if(!keys.next()) throw new SQLException("Enrollment id was not generated"); id=keys.getLong(1); }
          }
          conn.commit();
          return EnrollmentRecord.newBuilder().setId(id).setCourseCode(rs.getString("code")).setSection(rs.getString("section"))
              .setCourseName(rs.getString("name")).setSchedule(rs.getString("schedule")).setEnrolledAt(System.currentTimeMillis()/1000).build();
        }
      } catch (StatusRuntimeException e) { conn.rollback(); throw e; }
        catch (SQLIntegrityConstraintViolationException e) { conn.rollback(); throw Status.ALREADY_EXISTS.withDescription("You are already enrolled in this course").asRuntimeException(); }
        catch (SQLException e) { conn.rollback(); throw Status.INTERNAL.withDescription("Unable to enroll").withCause(e).asRuntimeException(); }
    } catch (SQLException e) { throw Status.INTERNAL.withDescription("Unable to enroll").withCause(e).asRuntimeException(); }
  }
  private int countEnrollments(Connection conn,long courseId) throws SQLException { try(PreparedStatement ps=conn.prepareStatement("SELECT COUNT(*) FROM enrollments WHERE course_id=?")){ps.setLong(1,courseId);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}} }
  private boolean alreadyEnrolled(Connection conn,String student,long courseId) throws SQLException { try(PreparedStatement ps=conn.prepareStatement("SELECT 1 FROM enrollments WHERE student_username=? AND course_id=?")){ps.setString(1,student);ps.setLong(2,courseId);try(ResultSet rs=ps.executeQuery()){return rs.next();}} }
  private EnrollmentRecord record(ResultSet rs) throws SQLException { return EnrollmentRecord.newBuilder().setId(rs.getLong("id")).setCourseCode(rs.getString("code")).setSection(rs.getString("section")).setCourseName(rs.getString("name")).setSchedule(rs.getString("schedule")).setEnrolledAt(rs.getTimestamp("enrolled_at").toInstant().getEpochSecond()).build(); }
  private AuthUser student() { AuthUser user=DevAuthInterceptor.USER.get(); if(user==null) throw Status.UNAUTHENTICATED.withDescription("Missing or invalid token").asRuntimeException(); if(!"STUDENT".equals(user.role())) throw Status.PERMISSION_DENIED.withDescription("Student role required").asRuntimeException(); return user; }
}

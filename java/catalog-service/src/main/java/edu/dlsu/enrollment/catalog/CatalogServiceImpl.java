package edu.dlsu.enrollment.catalog;

import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.devsecurity.DevAuthInterceptor;
import enrollment.catalog.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.sql.*;

public final class CatalogServiceImpl extends CatalogServiceGrpc.CatalogServiceImplBase {
  private final HikariDataSource dataSource;
  public CatalogServiceImpl(HikariDataSource dataSource) { this.dataSource = dataSource; }

  @Override public void listCourses(ListCoursesRequest request, StreamObserver<ListCoursesResponse> observer) {
    if (DevAuthInterceptor.USER.get() == null) { observer.onError(Status.UNAUTHENTICATED.withDescription("Missing or invalid token").asRuntimeException()); return; }
    String sql = "SELECT c.id,c.code,c.name,c.section,c.schedule,c.capacity,c.status,c.faculty_name,COUNT(e.id) enrolled " +
        "FROM courses c LEFT JOIN enrollments e ON e.course_id=c.id " + (request.getOpenOnly() ? "WHERE c.status='OPEN' " : "") +
        "GROUP BY c.id,c.code,c.name,c.section,c.schedule,c.capacity,c.status,c.faculty_name ORDER BY c.code,c.section";
    try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
      ListCoursesResponse.Builder response = ListCoursesResponse.newBuilder();
      while (rs.next()) response.addCourses(Course.newBuilder().setId(rs.getLong("id")).setCode(rs.getString("code"))
          .setName(rs.getString("name")).setSection(rs.getString("section")).setSchedule(rs.getString("schedule"))
          .setCapacity(rs.getInt("capacity")).setEnrolled(rs.getInt("enrolled")).setStatus(rs.getString("status"))
          .setFacultyName(rs.getString("faculty_name")).build());
      observer.onNext(response.build()); observer.onCompleted();
    } catch (SQLException e) { observer.onError(Status.INTERNAL.withDescription("Unable to list courses").withCause(e).asRuntimeException()); }
  }
}

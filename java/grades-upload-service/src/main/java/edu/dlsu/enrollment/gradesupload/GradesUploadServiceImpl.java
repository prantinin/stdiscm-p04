package edu.dlsu.enrollment.gradesupload;

import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.security.AuthContext;
import edu.dlsu.enrollment.security.AuthUser;
import enrollment.gradesupload.DeleteGradeRequest;
import enrollment.gradesupload.DeleteGradeResponse;
import enrollment.gradesupload.EditGradeRequest;
import enrollment.gradesupload.EditGradeResponse;
import enrollment.gradesupload.GradeRecord;
import enrollment.gradesupload.GradeUploadServiceGrpc;
import enrollment.gradesupload.ListUploadedGradesRequest;
import enrollment.gradesupload.ListUploadedGradesResponse;
import enrollment.gradesupload.UploadGradeRequest;
import enrollment.gradesupload.UploadGradeResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.sql.SQLException;
import java.util.Set;
import java.util.regex.Pattern;

public final class GradesUploadServiceImpl extends GradeUploadServiceGrpc.GradeUploadServiceImplBase {
  private static final Set<String> GRADES = Set.of("4.0", "3.5", "3.0", "2.5", "2.0", "1.5", "1.0", "0.0");
  private static final Pattern TERM = Pattern.compile("\\d{4}-T[1-3]");
  private final HikariDataSource dataSource;
  public GradesUploadServiceImpl(HikariDataSource dataSource) { this.dataSource = dataSource; }

  @Override public void listUploadedGrades(ListUploadedGradesRequest request, StreamObserver<ListUploadedGradesResponse> observer) {
    AuthUser user = faculty(observer);
    if (user == null) return;
    String sql = "SELECT id, student_username, course_code, term, grade, uploaded_by, uploaded_at FROM grades WHERE uploaded_by = ? ORDER BY term, course_code, student_username, id";
    try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
      statement.setString(1, user.username());
      var response = ListUploadedGradesResponse.newBuilder();
      try (var rows = statement.executeQuery()) {
        while (rows.next()) response.addGrades(toGradeRecord(rows));
      }
      observer.onNext(response.build());
      observer.onCompleted();
    } catch (SQLException e) {
      System.err.println("ListUploadedGrades database error: " + e.getMessage());
      observer.onError(Status.INTERNAL.withDescription("Unable to retrieve uploaded grades").asRuntimeException());
    }
  }

  @Override public void editGrade(EditGradeRequest request, StreamObserver<EditGradeResponse> observer) {
    AuthUser user = faculty(observer);
    if (user == null) return;
    String grade = request.getGrade().trim();
    if (request.getGradeId() <= 0 || !GRADES.contains(grade)) {
      observer.onError(Status.INVALID_ARGUMENT.withDescription("A valid grade ID and grade are required").asRuntimeException());
      return;
    }
    try (var connection = dataSource.getConnection();
         var statement = connection.prepareStatement("UPDATE grades SET grade = ? WHERE id = ? AND uploaded_by = ?")) {
      statement.setString(1, grade);
      statement.setLong(2, request.getGradeId());
      statement.setString(3, user.username());
      if (statement.executeUpdate() == 0) {
        observer.onError(Status.NOT_FOUND.withDescription("Grade not found").asRuntimeException());
        return;
      }
      GradeRecord updated = findGrade(connection, request.getGradeId(), user.username());
      if (updated == null) {
        observer.onError(Status.NOT_FOUND.withDescription("Grade not found").asRuntimeException());
        return;
      }
      observer.onNext(EditGradeResponse.newBuilder().setGrade(updated).build());
      observer.onCompleted();
    } catch (SQLException e) {
      System.err.println("EditGrade database error: " + e.getMessage());
      observer.onError(Status.INTERNAL.withDescription("Unable to edit grade").asRuntimeException());
    }
  }

  @Override public void deleteGrade(DeleteGradeRequest request, StreamObserver<DeleteGradeResponse> observer) {
    AuthUser user = faculty(observer);
    if (user == null) return;
    if (request.getGradeId() <= 0) {
      observer.onError(Status.INVALID_ARGUMENT.withDescription("A valid grade ID is required").asRuntimeException());
      return;
    }
    try (var connection = dataSource.getConnection();
         var statement = connection.prepareStatement("DELETE FROM grades WHERE id = ? AND uploaded_by = ?")) {
      statement.setLong(1, request.getGradeId());
      statement.setString(2, user.username());
      if (statement.executeUpdate() == 0) {
        observer.onError(Status.NOT_FOUND.withDescription("Grade not found").asRuntimeException());
        return;
      }
      observer.onNext(DeleteGradeResponse.newBuilder().setDeleted(true).build());
      observer.onCompleted();
    } catch (SQLException e) {
      System.err.println("DeleteGrade database error: " + e.getMessage());
      observer.onError(Status.INTERNAL.withDescription("Unable to delete grade").asRuntimeException());
    }
  }

  @Override public void uploadGrade(UploadGradeRequest request, StreamObserver<UploadGradeResponse> observer) {
    AuthUser user = AuthContext.current();
    if (user == null) { observer.onError(Status.UNAUTHENTICATED.withDescription("Missing or invalid token").asRuntimeException()); return; }
    if (!"FACULTY".equals(user.role())) { observer.onError(Status.PERMISSION_DENIED.withDescription("Faculty only").asRuntimeException()); return; }
    String student = request.getStudentUsername().trim(), course = request.getCourseCode().trim(), term = request.getTerm().trim(), grade = request.getGrade().trim();
    if (student.isEmpty() || course.isEmpty() || term.isEmpty() || grade.isEmpty()) { observer.onError(Status.INVALID_ARGUMENT.withDescription("All fields are required").asRuntimeException()); return; }
    if (student.length() > 32 || course.length() > 16 || term.length() > 16 || grade.length() > 4 || !TERM.matcher(term).matches() || !GRADES.contains(grade)) {
      observer.onError(Status.INVALID_ARGUMENT.withDescription("Invalid username, course, term, or grade").asRuntimeException()); return;
    }
    String sql = "INSERT INTO grades (student_username, course_code, term, grade, uploaded_by) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE grade = VALUES(grade), uploaded_by = VALUES(uploaded_by), uploaded_at = CURRENT_TIMESTAMP";
    try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
      statement.setString(1, student); statement.setString(2, course); statement.setString(3, term); statement.setString(4, grade); statement.setString(5, user.username()); statement.executeUpdate();
      try (var query = connection.prepareStatement("SELECT uploaded_at FROM grades WHERE student_username = ? AND course_code = ? AND term = ?")) {
        query.setString(1, student); query.setString(2, course); query.setString(3, term);
        try (var row = query.executeQuery()) { row.next(); observer.onNext(UploadGradeResponse.newBuilder().setStudentUsername(student).setCourseCode(course).setTerm(term).setGrade(grade).setUploadedAt(row.getTimestamp(1).getTime()).build()); observer.onCompleted(); }
      }
    } catch (SQLException e) { System.err.println("UploadGrade database error: " + e.getMessage()); observer.onError(Status.INTERNAL.withDescription("Unable to upload grade").asRuntimeException()); }
  }

  private AuthUser faculty(StreamObserver<?> observer) {
    AuthUser user = AuthContext.current();
    if (user == null) {
      observer.onError(Status.UNAUTHENTICATED.withDescription("Missing or invalid token").asRuntimeException());
      return null;
    }
    if (!"FACULTY".equals(user.role())) {
      observer.onError(Status.PERMISSION_DENIED.withDescription("Faculty only").asRuntimeException());
      return null;
    }
    return user;
  }

  private GradeRecord findGrade(java.sql.Connection connection, long id, String faculty) throws SQLException {
    try (var query = connection.prepareStatement("SELECT id, student_username, course_code, term, grade, uploaded_by, uploaded_at FROM grades WHERE id = ? AND uploaded_by = ?")) {
      query.setLong(1, id);
      query.setString(2, faculty);
      try (var row = query.executeQuery()) {
        return row.next() ? toGradeRecord(row) : null;
      }
    }
  }

  private GradeRecord toGradeRecord(java.sql.ResultSet row) throws SQLException {
    return GradeRecord.newBuilder()
        .setGradeId(row.getLong("id"))
        .setStudentUsername(row.getString("student_username"))
        .setCourseCode(row.getString("course_code"))
        .setTerm(row.getString("term"))
        .setGrade(row.getString("grade"))
        .setUploadedBy(row.getString("uploaded_by"))
        .setUploadedAt(row.getTimestamp("uploaded_at").getTime())
        .build();
  }
}

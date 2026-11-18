package edu.dlsu.enrollment.gradesupload;

import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.devsecurity.AuthUser;
import edu.dlsu.enrollment.devsecurity.DevAuthInterceptor;
import enrollment.gradesupload.GradeUploadServiceGrpc;
import enrollment.gradesupload.UploadGradeRequest;
import enrollment.gradesupload.UploadGradeResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.sql.SQLException;
import java.util.Set;
import java.util.regex.Pattern;

public final class GradesUploadServiceImpl extends GradeUploadServiceGrpc.GradeUploadServiceImplBase {
  private static final Set<String> GRADES = Set.of("4.0", "3.5", "3.0", "2.5", "2.0", "1.5", "1.0", "0.0");
  private static final Pattern TERM = Pattern.compile("\\d{4}-T[1-4]");
  private final HikariDataSource dataSource;
  public GradesUploadServiceImpl(HikariDataSource dataSource) { this.dataSource = dataSource; }

  @Override public void uploadGrade(UploadGradeRequest request, StreamObserver<UploadGradeResponse> observer) {
    AuthUser user = DevAuthInterceptor.USER.get();
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
}

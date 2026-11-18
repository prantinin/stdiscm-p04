package edu.dlsu.enrollment.gradesview;

import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.devsecurity.AuthUser;
import edu.dlsu.enrollment.devsecurity.DevAuthInterceptor;
import enrollment.gradesview.GetMyGradesRequest;
import enrollment.gradesview.GetMyGradesResponse;
import enrollment.gradesview.GradeRecord;
import enrollment.gradesview.GradesViewServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.sql.SQLException;

public final class GradesViewServiceImpl extends GradesViewServiceGrpc.GradesViewServiceImplBase {
  private final HikariDataSource dataSource;
  public GradesViewServiceImpl(HikariDataSource dataSource) { this.dataSource = dataSource; }

  @Override public void getMyGrades(GetMyGradesRequest request, StreamObserver<GetMyGradesResponse> observer) {
    AuthUser user = DevAuthInterceptor.USER.get();
    if (user == null) { observer.onError(Status.UNAUTHENTICATED.withDescription("Missing or invalid token").asRuntimeException()); return; }
    if (!"STUDENT".equals(user.role())) { observer.onError(Status.PERMISSION_DENIED.withDescription("Students only").asRuntimeException()); return; }
    try (var connection = dataSource.getConnection();
         var statement = connection.prepareStatement("SELECT student_username, course_code, term, grade, uploaded_by, uploaded_at FROM grades WHERE student_username = ? ORDER BY term, course_code")) {
      statement.setString(1, user.username());
      var response = GetMyGradesResponse.newBuilder();
      try (var rows = statement.executeQuery()) {
        while (rows.next()) response.addGrades(GradeRecord.newBuilder()
            .setStudentUsername(rows.getString("student_username"))
            .setCourseCode(rows.getString("course_code"))
            .setTerm(rows.getString("term"))
            .setGrade(rows.getString("grade"))
            .setUploadedBy(rows.getString("uploaded_by"))
            .setUploadedAt(rows.getTimestamp("uploaded_at").getTime()));
      }
      observer.onNext(response.build()); observer.onCompleted();
    } catch (SQLException e) { System.err.println("GetMyGrades database error: " + e.getMessage()); observer.onError(Status.INTERNAL.withDescription("Unable to retrieve grades").asRuntimeException()); }
  }
}

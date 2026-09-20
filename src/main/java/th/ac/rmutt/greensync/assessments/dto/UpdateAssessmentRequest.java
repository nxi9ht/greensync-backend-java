package th.ac.rmutt.greensync.assessments.dto;

import java.util.List;

public class UpdateAssessmentRequest {
  public String status;
  public Double total_score;
  public String certified_level;
  public Integer assessment_year;
  public String notes;
  public Integer assessor_user_id;
  public List<DetailUpdate> details;

  public static class DetailUpdate {
    public Integer assessment_detail_id;
    public Double self_score;
    public String applicant_comment;
    public Double assessor_score;
    public String auditor_comment;
  }
}

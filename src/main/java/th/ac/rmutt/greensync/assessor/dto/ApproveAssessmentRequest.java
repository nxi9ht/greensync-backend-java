package th.ac.rmutt.greensync.assessor.dto;

import java.util.List;

public class ApproveAssessmentRequest {
  public String notes;
  public Double total_score;
  public String certified_level;
  public String certificate_no;
  public String issued_at;
  public String expired_at;
  public String certificate_url;
  public List<Detail> details;

  public static class Detail {
    public Integer assessment_detail_id;
    public Double assessor_score;
    public String auditor_comment;
  }
}

package th.ac.rmutt.greensync.assessor.dto;

import java.util.List;

public class SaveEvidenceReviewRequest {
  public List<Detail> details;

  public static class Detail {
    public Integer assessment_detail_id;
    public String result; // "PASS" | "FAIL"
    public String auditor_comment;
    public Double assessor_score;
  }
}

package th.ac.rmutt.greensync.assessor.dto;

import java.util.List;

public class RequestRevisionRequest {
  public String notes;
  public List<Detail> details;

  public static class Detail {
    public Integer assessment_detail_id;
    public String auditor_comment;
  }
}

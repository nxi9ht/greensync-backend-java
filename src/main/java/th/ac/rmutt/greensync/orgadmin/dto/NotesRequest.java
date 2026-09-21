package th.ac.rmutt.greensync.orgadmin.dto;

import jakarta.validation.constraints.Size;

/** Shared shape for send-to-user (notes required) and resubmit (notes optional) — the
 * controller enforces the "required" rule for send-to-user explicitly. */
public class NotesRequest {
  @Size(max = 1000)
  public String notes;
}

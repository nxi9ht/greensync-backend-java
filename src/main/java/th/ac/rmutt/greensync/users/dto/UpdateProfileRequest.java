package th.ac.rmutt.greensync.users.dto;

public class UpdateProfileRequest {
  public String first_name;
  public String last_name;
  public String phone;
  public String profile_image;
  public String bio;
  public BankAccountRequest bank_account;

  public static class BankAccountRequest {
    public String bank_name;
    public String account_no;
    public String account_name;
  }
}

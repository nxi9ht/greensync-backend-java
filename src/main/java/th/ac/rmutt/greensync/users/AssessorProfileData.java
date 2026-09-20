package th.ac.rmutt.greensync.users;

/** Everything captured on the assessor self-registration form in one place. */
public record AssessorProfileData(
    String firstName,
    String lastName,
    String phone,
    String licenseNumber,
    Integer yearsExperience,
    String educationBackground,
    String qualificationFileUrl,
    String bankName,
    String bankAccountNo,
    String bankAccountName) {}

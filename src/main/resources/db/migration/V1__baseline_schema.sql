-- Baseline schema for a clean GreenSync PostgreSQL database.
-- Mirrors greensync-backend's TypeORM InitSchema migration table-for-table so
-- both backends can point at the same database during the NestJS-to-Java
-- migration. The orphaned "chat_log" (singular) table found in the live dev
-- database is intentionally not recreated here — it has no owning entity in
-- either backend.
DO $$ BEGIN
  CREATE TYPE notifications_type_enum AS ENUM ('SYSTEM','ASSESSMENT','ACCOUNT','DEADLINE','REQUEST','URGENT');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS organizations (
  org_id SERIAL PRIMARY KEY, name varchar(255) NOT NULL, tax_id varchar(13),
  industry_type varchar(100), number_of_employees integer, total_floor_area double precision,
  working_hours_per_year integer, base_year integer, target_reduction_percent double precision,
  target_year integer, industry_benchmark_value double precision, carbon_standard varchar(50),
  current_green_status varchar(50), stripe_customer_id varchar(50), stripe_subscription_id varchar(100),
  is_active boolean NOT NULL DEFAULT true, cached_executive_summary text, cached_recommendations text,
  last_summary_hash varchar(255), last_recommendations_hash varchar(255),
  last_summary_analyzed_at timestamp, last_recommendations_analyzed_at timestamp,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS organization_units (
  unit_id SERIAL PRIMARY KEY, org_id integer REFERENCES organizations(org_id) ON DELETE CASCADE,
  unit_name varchar(255) NOT NULL, parent_unit_id integer REFERENCES organization_units(unit_id) ON DELETE SET NULL,
  unit_type varchar(100), area double precision, created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS roles (
  role_id SERIAL PRIMARY KEY, role_name varchar(50) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS users (
  user_id SERIAL PRIMARY KEY, email varchar(255) NOT NULL UNIQUE, password_hash varchar(255) NOT NULL,
  is_active boolean NOT NULL DEFAULT true, password_setup_required boolean NOT NULL DEFAULT false,
  email_verified_at timestamp, last_login_at timestamp,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reset_password_token varchar(255), reset_password_expires timestamp,
  org_id integer REFERENCES organizations(org_id) ON DELETE CASCADE,
  org_unit_id integer REFERENCES organization_units(unit_id) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS user_profiles (
  user_id integer PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
  first_name varchar(100), last_name varchar(100), phone varchar(20), profile_image text,
  personal_goal_percent decimal(5,2), created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS user_roles (
  user_id integer REFERENCES users(user_id) ON DELETE CASCADE,
  role_id integer REFERENCES roles(role_id) ON DELETE CASCADE, PRIMARY KEY (user_id, role_id)
);
CREATE TABLE IF NOT EXISTS assessor_profiles (
  assessor_profile_id SERIAL PRIMARY KEY, user_id integer UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
  license_number varchar(100), years_experience integer, education_background text,
  qualification_file_url varchar(255), verification_status varchar(50), verified_at timestamp,
  verified_by integer REFERENCES users(user_id) ON DELETE SET NULL,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS bank_accounts (
  bank_account_id SERIAL PRIMARY KEY, user_id integer REFERENCES users(user_id) ON DELETE CASCADE,
  account_name varchar(255), account_no varchar(50), bank_name varchar(100),
  is_primary boolean NOT NULL DEFAULT false, created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS emission_factors (
  emission_factor_id SERIAL PRIMARY KEY, name varchar(255) NOT NULL, scope integer, unit varchar(50),
  factor_value double precision, year integer, source varchar(255),
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS carbon_activity_logs (
  carbon_log_id SERIAL PRIMARY KEY,
  emission_factor_id integer REFERENCES emission_factors(emission_factor_id) ON DELETE SET NULL,
  org_id integer REFERENCES organizations(org_id) ON DELETE CASCADE,
  org_unit_id integer REFERENCES organization_units(unit_id) ON DELETE SET NULL,
  activity_type varchar(100), month integer, year integer, usage_amount double precision,
  total_emission double precision, evidence_url text, data_source varchar(100),
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS green_criteria_master (
  criteria_id SERIAL PRIMARY KEY, category_number integer, criteria_code varchar(50),
  criteria_name varchar(255), max_score double precision, description text, year_version integer,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS assessments (
  assessment_id SERIAL PRIMARY KEY, org_id integer NOT NULL REFERENCES organizations(org_id),
  assessor_user_id integer REFERENCES users(user_id) ON DELETE SET NULL, assessment_year integer,
  status varchar(50) NOT NULL DEFAULT 'PENDING', total_score double precision NOT NULL DEFAULT 0,
  notes text, certified_level varchar(50), submitted_at timestamp,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS assessment_details (
  assessment_detail_id SERIAL PRIMARY KEY,
  assessment_id integer REFERENCES assessments(assessment_id) ON DELETE CASCADE,
  criteria_id integer REFERENCES green_criteria_master(criteria_id),
  self_score double precision NOT NULL DEFAULT 0, applicant_comment text,
  assessor_score double precision NOT NULL DEFAULT 0, auditor_comment text,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS evidence_files (
  evidence_file_id SERIAL PRIMARY KEY,
  assessment_detail_id integer REFERENCES assessment_details(assessment_detail_id) ON DELETE CASCADE,
  uploaded_by_user_id integer REFERENCES users(user_id) ON DELETE SET NULL,
  carbon_log_id integer REFERENCES carbon_activity_logs(carbon_log_id) ON DELETE SET NULL,
  file_name varchar(255), file_url text, file_type varchar(50), file_size bigint,
  category varchar(100), uploaded_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS certificates (
  certificate_id SERIAL PRIMARY KEY, certificate_no varchar(100) UNIQUE,
  assessment_id integer REFERENCES assessments(assessment_id) ON DELETE CASCADE,
  org_id integer REFERENCES organizations(org_id) ON DELETE CASCADE,
  issued_at timestamp, expired_at timestamp, certificate_url varchar(255),
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS assessment_audit_logs (
  audit_log_id SERIAL PRIMARY KEY,
  assessment_detail_id integer REFERENCES assessment_details(assessment_detail_id) ON DELETE SET NULL,
  action_by_user_id integer REFERENCES users(user_id) ON DELETE SET NULL,
  action varchar(100), comment text, created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS chat_logs (
  chat_log_id SERIAL PRIMARY KEY, user_id integer REFERENCES users(user_id) ON DELETE SET NULL,
  question text, answer text, intent varchar(100), session_id integer, session_title varchar(255),
  related_module varchar(100), confidence_score double precision,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS notifications (
  notification_id SERIAL PRIMARY KEY, title varchar(255) NOT NULL, message text NOT NULL,
  type notifications_type_enum NOT NULL DEFAULT 'SYSTEM', is_read boolean NOT NULL DEFAULT false,
  link varchar(255), created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  recipient_id integer NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  sender_id integer REFERENCES users(user_id) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS features (
  feature_id SERIAL PRIMARY KEY, feature_code varchar(50) UNIQUE, feature_name varchar(255),
  description text, created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS subscription_plans (
  plan_id SERIAL PRIMARY KEY, plan_name varchar(100) NOT NULL, description text, badge varchar(50),
  price_per_month double precision, stripe_price_id varchar(255), max_users integer, max_locations integer,
  is_active boolean NOT NULL DEFAULT true, created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS plan_features (
  plan_id integer REFERENCES subscription_plans(plan_id) ON DELETE CASCADE,
  feature_id integer REFERENCES features(feature_id) ON DELETE CASCADE, PRIMARY KEY (plan_id, feature_id)
);
CREATE TABLE IF NOT EXISTS organization_subscriptions (
  org_subscription_id SERIAL PRIMARY KEY,
  org_id integer REFERENCES organizations(org_id) ON DELETE CASCADE,
  plan_id integer REFERENCES subscription_plans(plan_id) ON DELETE CASCADE,
  start_date date, end_date date, status varchar(50), auto_renew boolean NOT NULL DEFAULT false,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS invoices (
  invoice_id SERIAL PRIMARY KEY, org_id integer REFERENCES organizations(org_id) ON DELETE CASCADE,
  plan_id integer REFERENCES subscription_plans(plan_id) ON DELETE SET NULL,
  amount double precision, status varchar(50), reference_number varchar(100), notes text,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS payments (
  payment_id SERIAL PRIMARY KEY, invoice_id integer REFERENCES invoices(invoice_id) ON DELETE SET NULL,
  org_id integer REFERENCES organizations(org_id) ON DELETE CASCADE, amount double precision NOT NULL,
  currency varchar(10) NOT NULL DEFAULT 'THB', payment_method varchar(50), payment_status varchar(50),
  paid_at timestamp, created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS feature_usage_logs (
  feature_usage_log_id SERIAL PRIMARY KEY,
  org_id integer REFERENCES organizations(org_id) ON DELETE CASCADE,
  feature_code varchar(50), usage_count integer NOT NULL DEFAULT 0,
  usage_date date NOT NULL DEFAULT CURRENT_DATE, usage_month integer, usage_year integer,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS system_settings (
  key varchar(50) PRIMARY KEY, value text, description varchar(255),
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_org ON users (org_id);
CREATE INDEX IF NOT EXISTS idx_assessments_org ON assessments (org_id);
CREATE INDEX IF NOT EXISTS idx_carbon_org_year_month ON carbon_activity_logs (org_id, year, month);
CREATE INDEX IF NOT EXISTS idx_invoices_org ON invoices (org_id);
CREATE INDEX IF NOT EXISTS idx_invoices_plan ON invoices (plan_id);
CREATE INDEX IF NOT EXISTS idx_invoices_created ON invoices (created_at);
CREATE INDEX IF NOT EXISTS idx_subscription_plans_price ON subscription_plans (price_per_month);
CREATE INDEX IF NOT EXISTS idx_subscription_plans_created ON subscription_plans (created_at);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications (recipient_id, created_at);

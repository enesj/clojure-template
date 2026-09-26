--
-- PostgreSQL database dump
--

-- Dumped from database version 13.23 (Debian 13.23-1.pgdg13+1)
-- Dumped by pg_dump version 14.18 (Homebrew)

-- Started on 2026-09-26 10:03:01 CEST

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

DROP DATABASE IF EXISTS multi_tenant_pos;
--
-- TOC entry 4018 (class 1262 OID 16384)
-- Name: multi_tenant_pos; Type: DATABASE; Schema: -; Owner: app_user
--

CREATE DATABASE multi_tenant_pos WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE = 'en_US.utf8';


ALTER DATABASE multi_tenant_pos OWNER TO app_user;

\connect multi_tenant_pos

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 4 (class 3079 OID 17351)
-- Name: fuzzystrmatch; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS fuzzystrmatch WITH SCHEMA public;


--
-- TOC entry 4019 (class 0 OID 0)
-- Dependencies: 4
-- Name: EXTENSION fuzzystrmatch; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION fuzzystrmatch IS 'determine similarities and distance between strings';


--
-- TOC entry 2 (class 3079 OID 17184)
-- Name: hstore; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS hstore WITH SCHEMA public;


--
-- TOC entry 4020 (class 0 OID 0)
-- Dependencies: 2
-- Name: EXTENSION hstore; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION hstore IS 'data type for storing sets of (key, value) pairs';


--
-- TOC entry 5 (class 3079 OID 17362)
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- TOC entry 4021 (class 0 OID 0)
-- Dependencies: 5
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- TOC entry 3 (class 3079 OID 17344)
-- Name: unaccent; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;


--
-- TOC entry 4022 (class 0 OID 0)
-- Dependencies: 3
-- Name: EXTENSION unaccent; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION unaccent IS 'text search dictionary that removes accents';


--
-- TOC entry 957 (class 1247 OID 17473)
-- Name: admin_invitation_role; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.admin_invitation_role AS ENUM (
    'support',
    'admin'
);


ALTER TYPE public.admin_invitation_role OWNER TO app_user;

--
-- TOC entry 960 (class 1247 OID 17478)
-- Name: admin_invitation_status; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.admin_invitation_status AS ENUM (
    'pending',
    'accepted',
    'expired',
    'revoked'
);


ALTER TYPE public.admin_invitation_status OWNER TO app_user;

--
-- TOC entry 787 (class 1247 OID 16402)
-- Name: admin_role; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.admin_role AS ENUM (
    'admin',
    'support',
    'owner'
);


ALTER TYPE public.admin_role OWNER TO app_user;

--
-- TOC entry 784 (class 1247 OID 16397)
-- Name: admin_status; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.admin_status AS ENUM (
    'active',
    'suspended'
);


ALTER TYPE public.admin_status OWNER TO app_user;

--
-- TOC entry 919 (class 1247 OID 17063)
-- Name: audit_actor_type; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.audit_actor_type AS ENUM (
    'user',
    'admin',
    'system'
);


ALTER TYPE public.audit_actor_type OWNER TO app_user;

--
-- TOC entry 929 (class 1247 OID 17119)
-- Name: backlog_status; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.backlog_status AS ENUM (
    'Waiting',
    'In progress',
    'Completed',
    'Need improvements'
);


ALTER TYPE public.backlog_status OWNER TO app_user;

--
-- TOC entry 932 (class 1247 OID 17129)
-- Name: backlog_type; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.backlog_type AS ENUM (
    'Issue',
    'Feature',
    'Refactoring',
    'Review',
    'Improvement'
);


ALTER TYPE public.backlog_type OWNER TO app_user;

--
-- TOC entry 857 (class 1247 OID 16680)
-- Name: currency; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.currency AS ENUM (
    'BAM',
    'EUR',
    'USD',
    'GBP',
    'CHF',
    'HRK',
    'RSD',
    'TRY'
);


ALTER TYPE public.currency OWNER TO app_user;

--
-- TOC entry 1010 (class 1247 OID 17772)
-- Name: expense_template_kind; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.expense_template_kind AS ENUM (
    'utility',
    'recurring',
    'generic'
);


ALTER TYPE public.expense_template_kind OWNER TO app_user;

--
-- TOC entry 1016 (class 1247 OID 17792)
-- Name: expense_template_status; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.expense_template_status AS ENUM (
    'active',
    'archived'
);


ALTER TYPE public.expense_template_status OWNER TO app_user;

--
-- TOC entry 815 (class 1247 OID 16495)
-- Name: invitation_role; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.invitation_role AS ENUM (
    'admin',
    'member',
    'viewer'
);


ALTER TYPE public.invitation_role OWNER TO app_user;

--
-- TOC entry 805 (class 1247 OID 16466)
-- Name: invitation_status; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.invitation_status AS ENUM (
    'pending',
    'accepted',
    'expired',
    'revoked'
);


ALTER TYPE public.invitation_status OWNER TO app_user;

--
-- TOC entry 904 (class 1247 OID 17001)
-- Name: login_principal_type; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.login_principal_type AS ENUM (
    'user',
    'admin'
);


ALTER TYPE public.login_principal_type OWNER TO app_user;

--
-- TOC entry 841 (class 1247 OID 16593)
-- Name: membership_role; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.membership_role AS ENUM (
    'owner',
    'admin',
    'member',
    'viewer'
);


ALTER TYPE public.membership_role OWNER TO app_user;

--
-- TOC entry 844 (class 1247 OID 16602)
-- Name: membership_status; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.membership_status AS ENUM (
    'active',
    'suspended'
);


ALTER TYPE public.membership_status OWNER TO app_user;

--
-- TOC entry 990 (class 1247 OID 17672)
-- Name: onboarding_step_kind; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.onboarding_step_kind AS ENUM (
    'name_workspace',
    'setup_profile',
    'rename_default_category',
    'configure_categories',
    'review_payer_types',
    'invite_members',
    'management_intro',
    'upload_first_receipt',
    'browse_intro'
);


ALTER TYPE public.onboarding_step_kind OWNER TO app_user;

--
-- TOC entry 987 (class 1247 OID 17664)
-- Name: onboarding_step_status; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.onboarding_step_status AS ENUM (
    'pending',
    'completed',
    'skipped'
);


ALTER TYPE public.onboarding_step_status OWNER TO app_user;

--
-- TOC entry 1003 (class 1247 OID 17745)
-- Name: payer_type; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.payer_type AS ENUM (
    'system',
    'custom'
);


ALTER TYPE public.payer_type OWNER TO app_user;

--
-- TOC entry 996 (class 1247 OID 17721)
-- Name: payment_provider_account_kind; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.payment_provider_account_kind AS ENUM (
    'user',
    'tenant'
);


ALTER TYPE public.payment_provider_account_kind OWNER TO app_user;

--
-- TOC entry 850 (class 1247 OID 16645)
-- Name: receipt_status; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.receipt_status AS ENUM (
    'uploaded',
    'parsing',
    'parsed',
    'extracting',
    'extracted',
    'review_required',
    'approved',
    'posted',
    'failed'
);


ALTER TYPE public.receipt_status OWNER TO app_user;

--
-- TOC entry 1013 (class 1247 OID 17780)
-- Name: recurrence_frequency; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.recurrence_frequency AS ENUM (
    'weekly',
    'monthly',
    'quarterly',
    'yearly',
    'custom'
);


ALTER TYPE public.recurrence_frequency OWNER TO app_user;

--
-- TOC entry 1026 (class 1247 OID 17868)
-- Name: recurring_expense_reminder_status; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.recurring_expense_reminder_status AS ENUM (
    'pending',
    'recorded',
    'skipped',
    'snoozed'
);


ALTER TYPE public.recurring_expense_reminder_status OWNER TO app_user;

--
-- TOC entry 808 (class 1247 OID 16476)
-- Name: tenant_status; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.tenant_status AS ENUM (
    'active',
    'suspended',
    'archived'
);


ALTER TYPE public.tenant_status OWNER TO app_user;

--
-- TOC entry 798 (class 1247 OID 16443)
-- Name: user_status; Type: TYPE; Schema: public; Owner: app_user
--

CREATE TYPE public.user_status AS ENUM (
    'active',
    'inactive',
    'suspended'
);


ALTER TYPE public.user_status OWNER TO app_user;

--
-- TOC entry 323 (class 1255 OID 17311)
-- Name: enforce_tenant_single_active_owner(); Type: FUNCTION; Schema: public; Owner: app_user
--

CREATE FUNCTION public.enforce_tenant_single_active_owner() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
       DECLARE
         affected_tenant_ids UUID[] := ARRAY[]::UUID[];
         affected_tenant_id UUID;
         active_owner_count INTEGER;
       BEGIN
         IF TG_TABLE_NAME = 'tenants' THEN
           IF TG_OP = 'DELETE' THEN
             affected_tenant_ids := array_append(affected_tenant_ids, OLD.id);
           ELSE
             affected_tenant_ids := array_append(affected_tenant_ids, NEW.id);
           END IF;
         ELSE
           IF TG_OP = 'DELETE' THEN
             affected_tenant_ids := array_append(affected_tenant_ids, OLD.tenant_id);
           ELSIF TG_OP = 'INSERT' THEN
             affected_tenant_ids := array_append(affected_tenant_ids, NEW.tenant_id);
           ELSE
             affected_tenant_ids := array_append(affected_tenant_ids, OLD.tenant_id);
             affected_tenant_ids := array_append(affected_tenant_ids, NEW.tenant_id);
           END IF;
         END IF;

         FOR affected_tenant_id IN
           SELECT DISTINCT tenant_id
           FROM unnest(affected_tenant_ids) AS tenant_id
           WHERE tenant_id IS NOT NULL
         LOOP
           IF EXISTS (SELECT 1 FROM tenants t WHERE t.id = affected_tenant_id) THEN
             SELECT COUNT(*)
               INTO active_owner_count
             FROM tenant_memberships tm
             WHERE tm.tenant_id = affected_tenant_id
               AND tm.role = 'owner'
               AND tm.status = 'active';

             IF active_owner_count <> 1 THEN
               RAISE EXCEPTION 'Tenant % must have exactly one active owner (found %)', affected_tenant_id, active_owner_count
                 USING ERRCODE = '23514';
             END IF;
           END IF;
         END LOOP;

         RETURN NULL;
       END;
       $$;


ALTER FUNCTION public.enforce_tenant_single_active_owner() OWNER TO app_user;

--
-- TOC entry 324 (class 1255 OID 17312)
-- Name: update_updated_at_column(); Type: FUNCTION; Schema: public; Owner: app_user
--

CREATE FUNCTION public.update_updated_at_column() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
       BEGIN
         NEW.updated_at = CURRENT_TIMESTAMP;
         RETURN NEW;
       END;
       $$;


ALTER FUNCTION public.update_updated_at_column() OWNER TO app_user;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 239 (class 1259 OID 17487)
-- Name: admin_invitations; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.admin_invitations (
    role public.admin_invitation_role NOT NULL,
    updated_at timestamp with time zone DEFAULT now(),
    email text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    invited_by uuid NOT NULL,
    token character varying(255) NOT NULL,
    status public.admin_invitation_status DEFAULT 'pending'::public.admin_invitation_status NOT NULL,
    id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    email_ciphertext text,
    email_lookup_hash character varying(64),
    email_key_version character varying(32)
);


ALTER TABLE public.admin_invitations OWNER TO app_user;

--
-- TOC entry 207 (class 1259 OID 16424)
-- Name: admin_sessions; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.admin_sessions (
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    user_agent text,
    last_activity timestamp with time zone NOT NULL,
    admin_id uuid NOT NULL,
    token character varying(255) NOT NULL,
    id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    ip_address text
);


ALTER TABLE public.admin_sessions OWNER TO app_user;

--
-- TOC entry 206 (class 1259 OID 16409)
-- Name: admins; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.admins (
    role public.admin_role DEFAULT 'admin'::public.admin_role NOT NULL,
    updated_at timestamp with time zone DEFAULT now(),
    email character varying(255) NOT NULL,
    password_hash text NOT NULL,
    last_login_at timestamp with time zone,
    status public.admin_status DEFAULT 'active'::public.admin_status NOT NULL,
    id uuid NOT NULL,
    full_name character varying(255),
    created_at timestamp with time zone DEFAULT now(),
    email_lookup_hash character varying(64),
    email_key_version character varying(32),
    email_ciphertext text,
    CONSTRAINT admins_email_check CHECK (((email)::text ~* '^[^\s@]+@[^\s@]+\.[^\s@]+$'::text))
);


ALTER TABLE public.admins OWNER TO app_user;

--
-- TOC entry 227 (class 1259 OID 16928)
-- Name: article_aliases; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.article_aliases (
    id uuid NOT NULL,
    supplier_id uuid,
    raw_label text,
    raw_label_normalized character varying(255) NOT NULL,
    article_id uuid,
    confidence integer DEFAULT 100,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    unit character varying(10) DEFAULT 'kom'::character varying NOT NULL
);


ALTER TABLE public.article_aliases OWNER TO app_user;

--
-- TOC entry 226 (class 1259 OID 16903)
-- Name: articles; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.articles (
    id uuid NOT NULL,
    canonical_name character varying(255) NOT NULL,
    normalized_key character varying(255) NOT NULL,
    subcategory_id uuid,
    link text,
    manufacturer_id uuid,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    unit character varying(10) DEFAULT 'kom'::character varying NOT NULL
);


ALTER TABLE public.articles OWNER TO app_user;

--
-- TOC entry 233 (class 1259 OID 17067)
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.audit_logs (
    updated_at timestamp with time zone DEFAULT now(),
    ip text,
    user_agent text,
    actor_type public.audit_actor_type NOT NULL,
    actor_id uuid,
    target_id uuid,
    target_type text,
    id uuid NOT NULL,
    action text NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    metadata jsonb
);


ALTER TABLE public.audit_logs OWNER TO app_user;

--
-- TOC entry 205 (class 1259 OID 16387)
-- Name: automigrate_migrations; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.automigrate_migrations (
    id integer NOT NULL,
    name character varying(255) NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.automigrate_migrations OWNER TO app_user;

--
-- TOC entry 204 (class 1259 OID 16385)
-- Name: automigrate_migrations_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user
--

CREATE SEQUENCE public.automigrate_migrations_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.automigrate_migrations_id_seq OWNER TO app_user;

--
-- TOC entry 4023 (class 0 OID 0)
-- Dependencies: 204
-- Name: automigrate_migrations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user
--

ALTER SEQUENCE public.automigrate_migrations_id_seq OWNED BY public.automigrate_migrations.id;


--
-- TOC entry 235 (class 1259 OID 17139)
-- Name: backlog; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.backlog (
    number integer NOT NULL,
    description text NOT NULL,
    status public.backlog_status DEFAULT 'Waiting'::public.backlog_status NOT NULL,
    type public.backlog_type NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.backlog OWNER TO app_user;

--
-- TOC entry 237 (class 1259 OID 17441)
-- Name: backlog_number_seq; Type: SEQUENCE; Schema: public; Owner: app_user
--

CREATE SEQUENCE public.backlog_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.backlog_number_seq OWNER TO app_user;

--
-- TOC entry 4024 (class 0 OID 0)
-- Dependencies: 237
-- Name: backlog_number_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user
--

ALTER SEQUENCE public.backlog_number_seq OWNED BY public.backlog.number;


--
-- TOC entry 224 (class 1259 OID 16878)
-- Name: categories; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.categories (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.categories OWNER TO app_user;

--
-- TOC entry 213 (class 1259 OID 16540)
-- Name: cities; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.cities (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    normalized_key character varying(255) NOT NULL,
    zip character varying(20),
    country character varying(255) DEFAULT 'Bosnia and Herzegovina'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.cities OWNER TO app_user;

--
-- TOC entry 212 (class 1259 OID 16533)
-- Name: countries; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.countries (
    country character varying(255) NOT NULL,
    code character varying(2) NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.countries OWNER TO app_user;

--
-- TOC entry 240 (class 1259 OID 17533)
-- Name: daily_exchange_rates; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.daily_exchange_rates (
    id uuid NOT NULL,
    currency_code character varying(3) NOT NULL,
    rate_date date NOT NULL,
    rate numeric(18,8) NOT NULL,
    fetched_at timestamp with time zone,
    is_fallback boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.daily_exchange_rates OWNER TO app_user;

--
-- TOC entry 229 (class 1259 OID 16979)
-- Name: dedup_ignored_clusters; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.dedup_ignored_clusters (
    id uuid NOT NULL,
    admin_id uuid NOT NULL,
    entity_type character varying(32) NOT NULL,
    cluster_id character varying(64) NOT NULL,
    member_ids jsonb NOT NULL,
    note text,
    created_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.dedup_ignored_clusters OWNER TO app_user;

--
-- TOC entry 222 (class 1259 OID 16852)
-- Name: email_verification_tokens; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.email_verification_tokens (
    updated_at timestamp with time zone DEFAULT now(),
    expires_at timestamp with time zone NOT NULL,
    last_attempted_at timestamp with time zone,
    attempts integer DEFAULT 0,
    user_id uuid NOT NULL,
    token character varying(255) NOT NULL,
    id uuid NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.email_verification_tokens OWNER TO app_user;

--
-- TOC entry 243 (class 1259 OID 17567)
-- Name: enabled_currencies; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.enabled_currencies (
    id uuid NOT NULL,
    code character varying(3) NOT NULL,
    name character varying(100) NOT NULL,
    is_base boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.enabled_currencies OWNER TO app_user;

--
-- TOC entry 244 (class 1259 OID 17576)
-- Name: exchange_rate_fetch_alerts; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.exchange_rate_fetch_alerts (
    id uuid NOT NULL,
    rate_date date NOT NULL,
    error_message text,
    fallback_source_date date,
    acknowledged boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.exchange_rate_fetch_alerts OWNER TO app_user;

--
-- TOC entry 220 (class 1259 OID 16793)
-- Name: expense_categories; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.expense_categories (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    exclude_from_reports boolean DEFAULT false NOT NULL,
    is_default boolean DEFAULT false NOT NULL
);


ALTER TABLE public.expense_categories OWNER TO app_user;

--
-- TOC entry 248 (class 1259 OID 17755)
-- Name: expense_contexts; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.expense_contexts (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_by_subject_ref character varying(64),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.expense_contexts OWNER TO app_user;

--
-- TOC entry 228 (class 1259 OID 16949)
-- Name: expense_items; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.expense_items (
    updated_at timestamp with time zone DEFAULT now(),
    line_total numeric(12,2) NOT NULL,
    alias_id uuid,
    article_id uuid,
    unit_price numeric(12,2),
    expense_id uuid NOT NULL,
    id uuid NOT NULL,
    qty numeric(10,3),
    created_at timestamp with time zone DEFAULT now(),
    tenant_id uuid NOT NULL,
    price_modified boolean DEFAULT false NOT NULL,
    unit character varying(10)
);


ALTER TABLE public.expense_items OWNER TO app_user;

--
-- TOC entry 250 (class 1259 OID 17846)
-- Name: expense_template_lines; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.expense_template_lines (
    updated_at timestamp with time zone DEFAULT now(),
    default_amount numeric(12,2),
    sort_order integer DEFAULT 0 NOT NULL,
    article_id uuid,
    is_active boolean DEFAULT true NOT NULL,
    label character varying(255) NOT NULL,
    id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    template_id uuid NOT NULL
);


ALTER TABLE public.expense_template_lines OWNER TO app_user;

--
-- TOC entry 249 (class 1259 OID 17797)
-- Name: expense_templates; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.expense_templates (
    updated_at timestamp with time zone DEFAULT now(),
    recurrence_interval integer DEFAULT 1,
    default_expense_category_id uuid,
    reminder_days_before integer DEFAULT 0 NOT NULL,
    default_expense_context_id uuid,
    name character varying(255) NOT NULL,
    default_currency public.currency DEFAULT 'BAM'::public.currency NOT NULL,
    recurrence_frequency public.recurrence_frequency,
    next_due_date date,
    default_supplier_id uuid,
    created_by_subject_ref character varying(64),
    status public.expense_template_status DEFAULT 'active'::public.expense_template_status NOT NULL,
    id uuid NOT NULL,
    default_payer_id uuid,
    kind public.expense_template_kind DEFAULT 'generic'::public.expense_template_kind NOT NULL,
    default_notes text,
    created_at timestamp with time zone DEFAULT now(),
    tenant_id uuid NOT NULL
);


ALTER TABLE public.expense_templates OWNER TO app_user;

--
-- TOC entry 221 (class 1259 OID 16805)
-- Name: expenses; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.expenses (
    purchased_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone DEFAULT now(),
    store_id uuid,
    expense_category_id uuid,
    currency public.currency DEFAULT 'BAM'::public.currency NOT NULL,
    supplier_id uuid,
    payer_id uuid NOT NULL,
    id uuid NOT NULL,
    notes text,
    total_amount numeric(12,2) NOT NULL,
    receipt_id uuid,
    created_at timestamp with time zone DEFAULT now(),
    tenant_id uuid NOT NULL,
    rate_fetched_at timestamp with time zone,
    exchange_rate numeric(18,8),
    original_amount numeric(12,2),
    bam_amount numeric(12,2),
    subject_ref character varying(64),
    created_by_subject_ref character varying(64),
    expense_context_id uuid
);


ALTER TABLE public.expenses OWNER TO app_user;

--
-- TOC entry 238 (class 1259 OID 17444)
-- Name: frontend_runtime_configs; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.frontend_runtime_configs (
    id uuid NOT NULL,
    scope character varying(32) NOT NULL,
    config_key character varying(64) NOT NULL,
    config_edn text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT frontend_runtime_configs_config_key_check CHECK (((config_key)::text = ANY ((ARRAY['entities'::character varying, 'view-options'::character varying, 'form-fields'::character varying, 'table-columns'::character varying, 'navigation'::character varying])::text[]))),
    CONSTRAINT frontend_runtime_configs_scope_check CHECK (((scope)::text = ANY ((ARRAY['admin'::character varying, 'user'::character varying])::text[])))
);


ALTER TABLE public.frontend_runtime_configs OWNER TO app_user;

--
-- TOC entry 241 (class 1259 OID 17541)
-- Name: global_settings; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.global_settings (
    id uuid NOT NULL,
    default_currency character varying(3) DEFAULT 'BAM'::character varying NOT NULL,
    default_note text,
    auto_publish_after_upload boolean DEFAULT false NOT NULL,
    ai_receipt_enhancement boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.global_settings OWNER TO app_user;

--
-- TOC entry 230 (class 1259 OID 17005)
-- Name: login_events; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.login_events (
    updated_at timestamp with time zone DEFAULT now(),
    ip text,
    user_agent text,
    principal_type public.login_principal_type NOT NULL,
    reason text,
    success boolean NOT NULL,
    id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    principal_id uuid NOT NULL
);


ALTER TABLE public.login_events OWNER TO app_user;

--
-- TOC entry 223 (class 1259 OID 16868)
-- Name: manufacturers; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.manufacturers (
    id uuid NOT NULL,
    display_name character varying(255) NOT NULL,
    normalized_key character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.manufacturers OWNER TO app_user;

--
-- TOC entry 245 (class 1259 OID 17649)
-- Name: onboarding_progress; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.onboarding_progress (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    role public.membership_role NOT NULL,
    dismissed boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.onboarding_progress OWNER TO app_user;

--
-- TOC entry 246 (class 1259 OID 17689)
-- Name: onboarding_steps; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.onboarding_steps (
    id uuid NOT NULL,
    progress_id uuid NOT NULL,
    kind public.onboarding_step_kind NOT NULL,
    step_order integer NOT NULL,
    status public.onboarding_step_status DEFAULT 'pending'::public.onboarding_step_status NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.onboarding_steps OWNER TO app_user;

--
-- TOC entry 234 (class 1259 OID 17098)
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.password_reset_tokens (
    id uuid NOT NULL,
    principal_type public.login_principal_type NOT NULL,
    principal_id uuid NOT NULL,
    token character varying(255) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.password_reset_tokens OWNER TO app_user;

--
-- TOC entry 218 (class 1259 OID 16687)
-- Name: payers; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.payers (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    label character varying(255) NOT NULL,
    is_default boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    is_active boolean DEFAULT true NOT NULL,
    type public.payer_type DEFAULT 'custom'::public.payer_type NOT NULL
);


ALTER TABLE public.payers OWNER TO app_user;

--
-- TOC entry 247 (class 1259 OID 17725)
-- Name: payment_provider_account_links; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.payment_provider_account_links (
    id uuid NOT NULL,
    account_kind public.payment_provider_account_kind NOT NULL,
    account_id uuid NOT NULL,
    provider character varying(64) NOT NULL,
    provider_customer_ref character varying(255) NOT NULL,
    status text DEFAULT 'active'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.payment_provider_account_links OWNER TO app_user;

--
-- TOC entry 236 (class 1259 OID 17166)
-- Name: raw_labels; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.raw_labels (
    id uuid NOT NULL,
    raw_label text NOT NULL,
    normalized_key character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.raw_labels OWNER TO app_user;

--
-- TOC entry 219 (class 1259 OID 16705)
-- Name: receipts; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.receipts (
    original_filename character varying(255),
    raw_extract_json jsonb,
    updated_at timestamp with time zone DEFAULT now(),
    total_amount_guess numeric(12,2),
    purchased_at_guess timestamp with time zone,
    file_size integer,
    store_alias_id uuid,
    currency_guess public.currency,
    store_guess character varying(255),
    expense_id uuid,
    status public.receipt_status DEFAULT 'uploaded'::public.receipt_status NOT NULL,
    file_hash character varying(64) NOT NULL,
    payer_id uuid,
    id uuid NOT NULL,
    content_type character varying(100),
    supplier_alias_id uuid,
    error_details jsonb,
    storage_key text NOT NULL,
    error_message text,
    supplier_guess character varying(255),
    retry_count integer DEFAULT 0,
    created_at timestamp with time zone DEFAULT now(),
    tenant_id uuid NOT NULL,
    parsed_markdown text,
    expense_category_id uuid,
    notes text,
    file_purged_at timestamp with time zone,
    subject_ref character varying(64),
    created_by_subject_ref character varying(64)
);


ALTER TABLE public.receipts OWNER TO app_user;

--
-- TOC entry 251 (class 1259 OID 17878)
-- Name: recurring_expense_reminders; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.recurring_expense_reminders (
    updated_at timestamp with time zone DEFAULT now(),
    skipped_at timestamp with time zone,
    status public.recurring_expense_reminder_status DEFAULT 'pending'::public.recurring_expense_reminder_status NOT NULL,
    snoozed_until timestamp with time zone,
    id uuid NOT NULL,
    due_date date NOT NULL,
    recorded_expense_id uuid,
    created_at timestamp with time zone DEFAULT now(),
    reminded_at timestamp with time zone DEFAULT now(),
    template_id uuid NOT NULL
);


ALTER TABLE public.recurring_expense_reminders OWNER TO app_user;

--
-- TOC entry 215 (class 1259 OID 16576)
-- Name: store_aliases; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.store_aliases (
    id uuid NOT NULL,
    raw_label text,
    raw_label_normalized character varying(255) NOT NULL,
    store_id uuid,
    confidence integer DEFAULT 100,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.store_aliases OWNER TO app_user;

--
-- TOC entry 214 (class 1259 OID 16556)
-- Name: stores; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.stores (
    id uuid NOT NULL,
    supplier_id uuid NOT NULL,
    display_name character varying(255) NOT NULL,
    normalized_key character varying(255) NOT NULL,
    address text,
    city_id uuid,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.stores OWNER TO app_user;

--
-- TOC entry 225 (class 1259 OID 16888)
-- Name: subcategories; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.subcategories (
    id uuid NOT NULL,
    category_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    is_system_default boolean DEFAULT true NOT NULL,
    tenant_id uuid,
    created_by_subject_ref character varying(64),
    is_active boolean DEFAULT true NOT NULL
);


ALTER TABLE public.subcategories OWNER TO app_user;

--
-- TOC entry 217 (class 1259 OID 16663)
-- Name: supplier_aliases; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.supplier_aliases (
    id uuid NOT NULL,
    raw_label text,
    raw_label_normalized character varying(255) NOT NULL,
    supplier_id uuid,
    confidence integer DEFAULT 100,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.supplier_aliases OWNER TO app_user;

--
-- TOC entry 211 (class 1259 OID 16523)
-- Name: suppliers; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.suppliers (
    id uuid NOT NULL,
    display_name character varying(255) NOT NULL,
    normalized_key character varying(255) NOT NULL,
    address text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.suppliers OWNER TO app_user;

--
-- TOC entry 210 (class 1259 OID 16501)
-- Name: tenant_invitations; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.tenant_invitations (
    role public.invitation_role NOT NULL,
    updated_at timestamp with time zone DEFAULT now(),
    email text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    invited_by uuid NOT NULL,
    token character varying(255) NOT NULL,
    status public.invitation_status DEFAULT 'pending'::public.invitation_status NOT NULL,
    id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    tenant_id uuid NOT NULL,
    email_lookup_hash character varying(64),
    email_key_version character varying(32),
    email_ciphertext text
);


ALTER TABLE public.tenant_invitations OWNER TO app_user;

--
-- TOC entry 216 (class 1259 OID 16607)
-- Name: tenant_memberships; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.tenant_memberships (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role public.membership_role NOT NULL,
    status public.membership_status DEFAULT 'active'::public.membership_status NOT NULL,
    invited_by uuid,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.tenant_memberships OWNER TO app_user;

--
-- TOC entry 242 (class 1259 OID 17554)
-- Name: tenant_settings; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.tenant_settings (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    email_notifications boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.tenant_settings OWNER TO app_user;

--
-- TOC entry 209 (class 1259 OID 16483)
-- Name: tenants; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.tenants (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    slug character varying(255) NOT NULL,
    status public.tenant_status DEFAULT 'active'::public.tenant_status NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.tenants OWNER TO app_user;

--
-- TOC entry 231 (class 1259 OID 17015)
-- Name: user_expense_settings; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.user_expense_settings (
    updated_at timestamp with time zone DEFAULT now(),
    receipt_ocr_provider text DEFAULT 'mistral'::text NOT NULL,
    id uuid NOT NULL,
    default_payer_id uuid,
    created_at timestamp with time zone DEFAULT now(),
    tenant_id uuid NOT NULL,
    subject_ref character varying(64)
);


ALTER TABLE public.user_expense_settings OWNER TO app_user;

--
-- TOC entry 232 (class 1259 OID 17048)
-- Name: user_sessions; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.user_sessions (
    id uuid NOT NULL,
    session_key character varying(255) NOT NULL,
    data jsonb NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.user_sessions OWNER TO app_user;

--
-- TOC entry 208 (class 1259 OID 16449)
-- Name: users; Type: TABLE; Schema: public; Owner: app_user
--

CREATE TABLE public.users (
    updated_at timestamp with time zone DEFAULT now(),
    email character varying(255) NOT NULL,
    avatar_url text,
    password_hash text NOT NULL,
    last_login_at timestamp with time zone,
    status public.user_status DEFAULT 'active'::public.user_status NOT NULL,
    id uuid NOT NULL,
    email_verified boolean DEFAULT false NOT NULL,
    full_name character varying(255),
    auth_provider text DEFAULT 'password'::text,
    created_at timestamp with time zone DEFAULT now(),
    email_ciphertext text,
    email_lookup_hash character varying(64),
    email_key_version character varying(32),
    CONSTRAINT users_email_check CHECK (((email)::text ~* '^[^\s@]+@[^\s@]+\.[^\s@]+$'::text))
);


ALTER TABLE public.users OWNER TO app_user;

--
-- TOC entry 3393 (class 2604 OID 16390)
-- Name: automigrate_migrations id; Type: DEFAULT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.automigrate_migrations ALTER COLUMN id SET DEFAULT nextval('public.automigrate_migrations_id_seq'::regclass);


--
-- TOC entry 3484 (class 2604 OID 17443)
-- Name: backlog number; Type: DEFAULT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.backlog ALTER COLUMN number SET DEFAULT nextval('public.backlog_number_seq'::regclass);


--
-- TOC entry 4000 (class 0 OID 17487)
-- Dependencies: 239
-- Data for Name: admin_invitations; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.admin_invitations (role, updated_at, email, expires_at, invited_by, token, status, id, created_at, email_ciphertext, email_lookup_hash, email_key_version) FROM stdin;
\.


--
-- TOC entry 3968 (class 0 OID 16424)
-- Dependencies: 207
-- Data for Name: admin_sessions; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.admin_sessions (updated_at, expires_at, user_agent, last_activity, admin_id, token, id, created_at, ip_address) FROM stdin;
2026-09-25 14:11:02.863683+00	2026-09-25 22:11:02.85967+00	curl/8.7.1	2026-09-25 14:11:02.85967+00	77e9697e-3564-4269-9985-61e95746d8e0	0ad1a3602b1a8eefd5022f43f9cc050c8a7b6a67dce5f07450aa1e820976036a	7d9dca0c-f564-4f9c-a008-ff7947054957	2026-09-25 14:11:02.85967+00	0:0:0:0:0:0:0:1
2026-09-26 07:42:46.98127+00	2026-09-26 15:37:36.961497+00	Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36	2026-09-26 07:42:46.979171+00	77e9697e-3564-4269-9985-61e95746d8e0	4ee078464fb3e9136e4a0ab610adecfa8cdc7817222aae03a228552573c8539e	5ebe80c0-cac0-49f1-a356-ee27436b1ffe	2026-09-26 07:37:36.961497+00	0:0:0:0:0:0:0:1
2026-09-25 14:12:11.547046+00	2026-09-25 22:11:36.928724+00	Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36	2026-09-25 14:12:11.546529+00	77e9697e-3564-4269-9985-61e95746d8e0	c7812e9283a307ddb48818cc76a233a22f4756470c623ce1c9525eea4ede634f	5af194ca-8af0-404b-954a-ca83f7c6ff70	2026-09-25 14:11:36.928724+00	0:0:0:0:0:0:0:1
2026-09-25 14:20:19.559006+00	2026-09-25 22:20:19.558295+00	curl/8.7.1	2026-09-25 14:20:19.558295+00	77e9697e-3564-4269-9985-61e95746d8e0	fa8c767f1f3f141e935f069e8d3c73dbbe1bf6b4d3796bd9a6c6a584529b0f37	482e5d23-dc59-4122-8def-75137ee493df	2026-09-25 14:20:19.558295+00	0:0:0:0:0:0:0:1
2026-09-25 14:20:52.087071+00	2026-09-25 22:20:52.086975+00	curl/8.7.1	2026-09-25 14:20:52.086975+00	77e9697e-3564-4269-9985-61e95746d8e0	7ebd7bab990c24292350ca179bdcea4005d4c6aa4119c21ba5e78437653e4913	b37c95ca-72eb-4420-9d2a-f54ae5e06903	2026-09-25 14:20:52.086975+00	0:0:0:0:0:0:0:1
\.


--
-- TOC entry 3967 (class 0 OID 16409)
-- Dependencies: 206
-- Data for Name: admins; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.admins (role, updated_at, email, password_hash, last_login_at, status, id, full_name, created_at, email_lookup_hash, email_key_version, email_ciphertext) FROM stdin;
owner	2026-09-26 07:37:36.970742+00	admin@example.com	bcrypt+sha512$ad670363c8b643401a4f22d1ec56d6c3$12$6eca4f69ac7b5df16275879a8b77c38b8889bd2238f56cb0	2026-09-26 07:37:36.968494+00	active	77e9697e-3564-4269-9985-61e95746d8e0	System Administrator	2026-09-24 18:44:50.005007+00	4TTM2pch5a4t8LItfLh9XgEkom1HbH/Hpy/9GKJr5nY=	v1	X73nHJW/3gqKWwpSTZz1JoSZK5AT9j8tMlBeSEzzuiQgEn+OOyfvjuMffEJm
\.


--
-- TOC entry 3988 (class 0 OID 16928)
-- Dependencies: 227
-- Data for Name: article_aliases; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.article_aliases (id, supplier_id, raw_label, raw_label_normalized, article_id, confidence, created_at, updated_at, unit) FROM stdin;
\.


--
-- TOC entry 3987 (class 0 OID 16903)
-- Dependencies: 226
-- Data for Name: articles; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.articles (id, canonical_name, normalized_key, subcategory_id, link, manufacturer_id, created_at, updated_at, unit) FROM stdin;
\.


--
-- TOC entry 3994 (class 0 OID 17067)
-- Dependencies: 233
-- Data for Name: audit_logs; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.audit_logs (updated_at, ip, user_agent, actor_type, actor_id, target_id, target_type, id, action, created_at, metadata) FROM stdin;
\.


--
-- TOC entry 3966 (class 0 OID 16387)
-- Dependencies: 205
-- Data for Name: automigrate_migrations; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.automigrate_migrations (id, name, created_at) FROM stdin;
1	0001_schema	2026-09-24 20:44:49.531955
2	0002_enable_hstore_extension	2026-09-24 20:44:49.559479
3	0003_function_enforce-tenant-single-active-owner	2026-09-24 20:44:49.577153
4	0004_function_update-updated-at-column	2026-09-24 20:44:49.59081
5	0005_trigger_payers-updated-at-trigger	2026-09-24 20:44:49.604042
6	0006_trigger_expenses-updated-at-trigger	2026-09-24 20:44:49.616573
7	0007_trigger_tenant-memberships-require-active-owner-trigger	2026-09-24 20:44:49.62865
8	0008_trigger_admins-updated-at-trigger	2026-09-24 20:44:49.640936
9	0009_trigger_user-expense-settings-updated-at-trigger	2026-09-24 20:44:49.653957
10	0010_trigger_categories-updated-at-trigger	2026-09-24 20:44:49.66768
11	0011_trigger_tenants-require-active-owner-trigger	2026-09-24 20:44:49.680317
12	0012_trigger_users-updated-at-trigger	2026-09-24 20:44:49.692157
13	0013_trigger_audit-logs-updated-at-trigger	2026-09-24 20:44:49.703382
14	0014_trigger_stores-updated-at-trigger	2026-09-24 20:44:49.715214
15	0015_trigger_manufacturers-updated-at-trigger	2026-09-24 20:44:49.726351
16	0016_trigger_store-aliases-updated-at-trigger	2026-09-24 20:44:49.737965
17	0017_trigger_cities-updated-at-trigger	2026-09-24 20:44:49.750207
18	0018_trigger_countries-updated-at-trigger	2026-09-24 20:44:49.76299
19	0019_trigger_expense-items-updated-at-trigger	2026-09-24 20:44:49.774473
20	0020_trigger_article-aliases-updated-at-trigger	2026-09-24 20:44:49.78543
21	0021_trigger_password-reset-tokens-updated-at-trigger	2026-09-24 20:44:49.79929
22	0022_trigger_backlog-updated-at-trigger	2026-09-24 20:44:49.810492
23	0023_trigger_email-verification-tokens-updated-at-trigger	2026-09-24 20:44:49.822357
24	0024_trigger_subcategories-updated-at-trigger	2026-09-24 20:44:49.834546
25	0025_trigger_login-events-updated-at-trigger	2026-09-24 20:44:49.846507
26	0026_trigger_receipts-updated-at-trigger	2026-09-24 20:44:49.858241
27	0027_trigger_articles-updated-at-trigger	2026-09-24 20:44:49.870006
28	0028_trigger_suppliers-updated-at-trigger	2026-09-24 20:44:49.882117
29	0029_trigger_admin-sessions-updated-at-trigger	2026-09-24 20:44:49.893985
30	0030_trigger_expense-categories-updated-at-trigger	2026-09-24 20:44:49.909795
31	0031_trigger_raw-labels-updated-at-trigger	2026-09-24 20:44:49.923968
32	0032_trigger_supplier-aliases-updated-at-trigger	2026-09-24 20:44:49.935594
33	0033_trigger_payer-types-updated-at-trigger	2026-09-24 20:44:49.947344
34	0034_enable_text_search_extensions	2026-09-24 20:44:49.971717
35	0035_seed_bosnia	2026-09-24 20:44:49.983902
36	0036_backlog_number_autoincrement	2026-09-24 20:44:49.997665
37	0037_seed_default_admin	2026-09-24 20:44:50.010236
38	0038_schema	2026-09-24 20:44:50.030271
39	0039_reset_frontend_runtime_table_columns	2026-09-24 20:44:50.04229
40	0040_schema	2026-09-24 20:44:50.056453
41	0041_expenses_supplier_id_nullable	2026-09-24 20:44:50.070162
42	0042_schema	2026-09-24 20:44:50.105197
43	0043_schema	2026-09-24 20:44:50.119895
44	0044_schema	2026-09-24 20:44:50.136922
45	0045_schema	2026-09-24 20:44:50.192018
46	0046_seed_settings_hierarchy	2026-09-24 20:44:50.206461
47	0047_schema	2026-09-24 20:44:50.222423
48	0048_audit_system_actor_type	2026-09-24 20:44:50.235241
49	0049_schema	2026-09-24 20:44:50.247719
50	0050_backlog_fix_enum_typos	2026-09-24 20:44:50.261074
51	0051_schema	2026-09-24 20:44:50.273078
52	0052_onboarding_tables	2026-09-24 20:44:50.284602
53	0053_receipt_file_purged_at	2026-09-24 20:45:36.819951
54	0054_schema	2026-09-24 20:45:36.833871
55	0055_schema	2026-09-24 20:45:36.85091
56	0056_schema	2026-09-24 20:45:36.867566
57	0057_schema	2026-09-24 20:45:36.883598
58	0058_schema	2026-09-24 20:45:36.902858
59	0059_schema	2026-09-24 20:45:36.921726
60	0060_schema	2026-09-24 20:45:36.962355
61	0061_schema	2026-09-24 20:45:36.993914
62	0062_schema	2026-09-24 20:57:27.110277
63	0063_schema	2026-09-24 20:57:27.127312
64	0064_legacy_email_index_aliases	2026-09-24 20:57:27.142067
65	0065_legacy_email_index_cleanup	2026-09-24 20:57:27.157413
66	0066_schema	2026-09-24 20:57:27.17841
67	0067_schema	2026-09-24 20:57:27.196835
68	0068_schema	2026-09-24 20:57:27.216133
69	0069_schema	2026-09-24 20:57:27.231289
70	0070_expenses_is_posted_index_realign	2026-09-24 20:57:27.250781
71	0071_expenses_is_posted_drop	2026-09-24 20:57:27.263375
72	0072_schema	2026-09-24 20:57:27.279066
73	0073_privacy_subject_refs	2026-09-24 20:57:27.309755
74	0074_schema	2026-09-24 20:57:27.330541
75	0075_schema	2026-09-24 20:57:27.344555
76	0076_schema	2026-09-24 20:57:27.421539
77	0077_schema	2026-09-24 20:57:27.448692
78	0078_seed_utilities_subscriptions_taxonomy	2026-09-24 20:57:27.464312
79	0079_drop_trigger_payer-types-updated-at-trigger	2026-09-24 21:02:50.970215
\.


--
-- TOC entry 3996 (class 0 OID 17139)
-- Dependencies: 235
-- Data for Name: backlog; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.backlog (number, description, status, type, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3985 (class 0 OID 16878)
-- Dependencies: 224
-- Data for Name: categories; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.categories (id, name, description, created_at, updated_at) FROM stdin;
eb40db31-554d-432b-a507-01d1c9a229fc	Utilities	Utility bills and services such as electricity, water, gas, and heating.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00
8da33e1c-1a82-4545-924c-2fc7be4cf329	Subscriptions	Subscription and recurring services such as mobile, internet, hosting, AI tools, memberships, and donations.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00
\.


--
-- TOC entry 3974 (class 0 OID 16540)
-- Dependencies: 213
-- Data for Name: cities; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.cities (id, name, normalized_key, zip, country, created_at, updated_at) FROM stdin;
b3638816-14ce-4612-b18e-e254bc7ddacf	Gornji Štrpci	gornji strpci	78439	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ce4a9953-9cb2-4e30-af26-e8cf6a63810a	Orašje	orasje	76270	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
908f5bec-217d-473c-bc1f-0311cef6f67d	Donji Vijačani	donji vijacani	78432	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3dd98adb-fe52-4f91-b39e-24078b23703d	Čapljina	capljina	88300	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2e72b79f-8ea6-43ef-b2a8-3bb8c22c9c33	Blažuj	blazuj	71215	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
478eccb5-b8a6-4bc1-894d-9d9e07eba981	Konjic	konjic	88400	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
296c82a7-9438-4b5d-baa0-e5ab50542025	Kruševo	krusevo	88203	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
498a9dc9-6eee-45f4-bf7d-c9b1c6a7a746	Čatići	catici	72246	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
afbbdd9b-e7ad-42d1-a6ed-8a51fef17281	Pajić Polje	pajic polje	70243	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
148bb524-c137-4607-a79c-328bbef8f15d	Blagaj	blagaj	88201	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
efb0014e-d7b4-485d-ad40-b227c4ff44cd	Istočno Sarajevo	istocno sarajevo	71123	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0a9acc76-e2a7-4032-9e1c-6fbcf58426a8	Jezero	jezero	70206	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b4f1b054-d7e1-4ead-8813-68cd149ac048	Cazin	cazin	77220	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9fe013ea-3217-4c6d-bcb2-1493a61b9503	Busovača	busovaca	72260	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b4f9a257-a004-4301-b242-0c76cf5e2706	Čajniče	cajnice	73280	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
29fcb2aa-f814-487b-9927-e1d732fb0766	Fojnica	fojnica	71270	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
dda21e44-edba-4924-be09-3d328e4a642b	Obudovac	obudovac	76235	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e8764061-658b-466e-88bd-f19d714999f1	Maslovare	maslovare	78223	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5437181a-87b7-4b19-9426-7c42c801d7ae	Mehurići	mehurici	72282	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5db84520-56fd-4c4e-b9b7-2897c30e382e	Naseobina Lišnja	naseobina lisnja	78434	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
dfb8f63e-2296-4e43-ba78-e3216218bd75	Nišići	nisici	71383	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3cf1da84-602c-4290-a1a4-27cdadad01d1	Glavičice	glavicice	76318	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
43abc94c-44e5-4f7a-a7cc-f7dfc409064b	Lopare	lopare	75240	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0c886113-0127-423f-aad5-a2871b26cbd2	Gradiška	gradiska	78400	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
aa45a744-e393-4d4e-9141-64d8cec18003	Husino	husino	75216	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
76d7ae39-a9ff-4e43-a672-0f6d152ef267	Bosanski Petrovac	bosanski petrovac	77250	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6e5aafee-0ab0-492e-863b-0826222cc98d	Krasulje	krasulje	79284	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6346b751-e909-4461-bbaf-9dc20a4a6b39	Dubrave Donje	dubrave donje	75274	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9a775fbb-ca81-48df-bf87-4061ef1a4bca	Gušteri	gusteri	75404	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
209a7c26-f184-4098-bb67-f4e44d8b46fe	Novi Šeher	novi seher	74254	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
10faccb7-b60c-40d1-9f14-2e7a5cec67d3	Gornji Podgradci	gornji podgradci	78405	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
26b77087-a63c-4dc8-84aa-91d684d175f6	Hrvadani	hrvadani	78436	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
97cc3c5f-5f77-4253-9d4d-d08a09c316e4	Miloševac	milosevac	74485	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7e48e137-1a5d-4845-aa7b-23708294380e	Kaljina	kaljina	71355	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
58d7f127-df42-47f2-aa84-b07d7d593fc3	Krnjeuša	krnjeusa	77253	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4cf5fb5e-cad5-4aab-b440-7d7eef4eddb9	Globarica	globarica	74258	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d1cc7a96-be26-4af0-bcdb-8b99bfab15db	Miljevina	miljevina	73313	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
06a06b27-a542-4207-bbcc-2204a34d063a	Grab	grab	89201	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5c9605b5-0675-497f-a01b-b59483c03d43	Gornja Dubica	gornja dubica	76296	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b91af294-3cb8-44f0-a47d-d32d264162d7	Drvetine	drvetine	70237	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
63b1a2da-70f9-419f-a208-85ee702e96d6	Kozarska Dubica	kozarska dubica	79240	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3d4a4d50-f91b-4de7-a522-cabbe2abf097	Lištani	listani	80204	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
48bb9ece-61fe-4e48-91fa-5d4429a16ce7	Mosko	mosko	89204	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
bbe4de56-92e5-4073-b5d4-c00aebbb45c5	Kalinovik	kalinovik	71230	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c018f44d-0ad6-48a0-b645-a91f1ceb46f3	Bijeljinsko Suho Polje	bijeljinsko suho polje	76321	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d29f42ba-dd38-4e1d-b80c-d15f2afd98f9	Gračanica	gracanica	75320	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3635e1ab-3cf0-4b6f-9ff0-5ffbc0ed2a29	Koraj	koraj	75247	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f41760ae-1343-4b63-aabc-0d2cf55ab7f6	Dobrljin	dobrljin	79223	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
bf7cc9f7-55cf-409d-883a-ca4d0ebd4c72	Babanovac	babanovac	72286	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
25a099ee-8d0b-4a79-8b1d-ee35333681ff	Brekovica	brekovica	77205	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
a2b2cef3-65d1-4b02-a139-12e5f2280545	Donji Agići	donji agici	79228	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
52e7d396-ae73-40b3-a120-139bbedf8a10	Domaljevac	domaljevac	76233	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
05f11077-b118-4a6d-967e-81ad78488e4e	Han Pijesak	han pijesak	71360	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ad01b30f-8999-4e58-963d-ade40807aebf	Brezovo Polje	brezovo polje	76216	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
37e66c60-d0a1-42e5-a68e-bcba5dadc4df	Gacko	gacko	89240	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f5315998-7307-4289-ac85-e69bb74aa040	Međugorje	medugorje	88266	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
640a5745-a034-4cf5-ab3a-3f9462a45920	Nova Topola	nova topola	78418	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e33b1b28-53fe-4042-8a7b-e61b65c45c81	Drinjača	drinjaca	75410	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3a4d8b2d-b013-48b5-ad6c-69a165d1792e	Dvorovi	dvorovi	76311	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
dcf4099e-d4f5-483a-a50b-62ddfc1e2a5e	Berkovići	berkovici	88363	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
50de7f64-0346-4b8a-84e6-ad25ec35420a	Duboki Potok	duboki potok	75358	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9da8fa64-f756-4ed1-a50f-51a0acb6c6df	Doralići	doralici	77226	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
8e7be284-3e12-480a-abe2-4c00a6bfd7dc	Biletić Polje	biletic polje	88268	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
54569592-47c1-46f4-90ee-de0c59d141b9	Maglaj	maglaj	74250	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
40cb2095-ac40-4351-ba0f-80cbf917fbe5	Buturović Polje	buturovic polje	88409	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
38c2fbb6-fbbb-48cb-86bc-21f339bd7792	Donji Svilaj	donji svilaj	76297	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b88ee83e-d42c-471f-aae7-a9cd12580eff	Kiseljak	kiseljak	71250	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
a41d3f86-081e-4edd-a76b-3d3d20712698	Čečava	cecava	74274	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
696bc319-ef9b-47d9-b742-cbd9d6bbefbc	Brestovo	brestovo	74210	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
8301fa96-fef6-4c03-8877-fa678634340b	Ostrožac kod Cazina	ostrozac kod cazina	77228	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e2e88695-eb4e-48e3-910f-e5fcbd1f4063	Bilješevo	biljesevo	72248	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ca001ee2-33ee-4ebc-9433-e340d6cbe679	Bijelo Brdo	bijelo brdo	73263	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0f969cbd-4e11-4ce5-8583-180d29765398	Demirovac	demirovac	79243	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5f19dea1-75b3-4bba-aff6-459136fa4e2d	Jablanica	jablanica	88420	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
fe5980cc-7de9-43e4-b37f-3d5ffca05d3d	Brodanac	brodanac	88243	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f0732e44-3ffd-408e-b600-f6fb5d086d91	Aleksandrovac	aleksandrovac	78255	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9a39f24d-da0c-461e-a15a-cf84023825ee	Čelinac	celinac	78240	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
22089f78-5eab-4f6b-811c-e3d38d45809b	Gornji Teslić	gornji teslic	74272	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
deb9850d-d1c9-41a8-9352-e18d3b106691	Buletić	buletic	74277	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9789eb07-e104-4746-867e-5b2022be1a6a	Johova	johova	79244	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
88c619c4-e231-459d-8c17-02a0ce8723a0	Oštra Luka	ostra luka	76279	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7a868f81-5fc0-40a4-b140-03b76d7498aa	Janjići	janjici	72215	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
bd75ddd3-bebd-4ec5-b911-28ca1460436f	Patkovača	patkovaca	76315	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
105f963b-6c7a-432c-b92f-4537a9370122	Doborovci	doborovci	75328	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f86ab7b3-a6e3-40d6-802d-c23b5367ff60	Derventa	derventa	74400	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
51f658f4-f036-40ec-8617-da34ab837965	Bok	bok	76277	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
55cbdb3b-1508-4a7c-beef-4ecf079f611e	Krupa na Vrbasu	krupa na vrbasu	78206	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
da2aab1e-85c8-47b3-8bca-a388fdb63bfa	Ostrožac	ostrozac	88423	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
df362d53-f540-41f9-9bcd-54cabdd1fd11	Kaonik	kaonik	72265	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
25f9ecc8-97ec-41fd-907d-a5a8e138087a	Gornji Smrtići	gornji smrtici	78438	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
62a233d2-c6c9-42f6-9747-7a9a71b110f2	Livno	livno	80101	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ed340fc2-b322-484a-a01e-321a27c8d388	Boljanić	boljanic	74322	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6a7b9d01-423c-4779-821f-d856c1fbbe0f	Lukavica	lukavica	75327	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7db4ac26-89a5-4d79-bdb8-e4a107f01751	Čelebići	celebici	73307	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9271d83b-3ffe-4ac8-b7f0-7cc92b1f68e4	Kupres	kupres	80320	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
06f13fa5-0554-4bea-a16b-27bbdcef9c6b	Kakanj	kakanj	72240	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2263deae-fbf2-4c72-8298-f8ae94fecbd4	Nova Kasaba	nova kasaba	75445	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
92bfb15a-09c2-43b7-b02a-e92648da1ce8	Mesihovina	mesihovina	80243	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
af250f77-78ae-47fa-9dbf-37a326717b55	Kaduni	kaduni	72264	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
486aafa9-6b43-4bba-ae9e-ff967116e12a	Majevac	majevac	74216	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7bf1a7a0-44b9-462c-836d-1e8595efafa6	Donje Moštre	donje mostre	71305	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ad43b768-6381-4336-a459-4938c85777f7	Bugojno	bugojno	70230	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4ab1bed3-d47e-456d-af8e-9d356fca9a33	Drvar	drvar	80260	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5dc7c02c-0c7b-4519-8390-3d79990cb5ce	Kneževo	knezevo	78230	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
18357670-d4ed-4593-bb4f-69413b71027b	Donji Vrbljani	donji vrbljani	79289	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
98e99587-37e5-4673-9cff-9c0dc18dadc3	Bratunac	bratunac	75420	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e4b8cd3a-56ef-4093-9d9d-3ecddd786ed0	Čardak	cardak	72224	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c7e557e2-e08e-4809-b6a2-8fa24317b156	Ljubuški	ljubuski	88320	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
cbaa4eba-efc8-4cbd-9a7f-f9a24cd8bf20	Mašići	masici	78410	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
655d1a58-ea9f-4d5d-b787-f2815bde4681	Donja Slatina	donja slatina	76237	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
db1767a9-dfc5-491c-9fa6-7801b3dd8e4d	Gornji Vakuf	gornji vakuf	70240	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2958c2a8-00d3-4d4a-aa2c-8444bf26bc35	Kiseljak kod Tuzle	kiseljak kod tuzle	75211	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e1319d56-f987-4092-8a0b-dd2a79c426e1	Brestovsko	brestovsko	71255	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9703f4e0-1de7-4355-8234-6ecc705643e4	Novi Grad	novi grad	79220	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
82cb83fd-b33e-4cd9-b615-93dc9ddca745	Međuvođe	meduvode	79247	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6b17a9f5-6399-45ae-a28d-1e577f7d07a2	Avtovac	avtovac	89245	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
619ade89-3fd9-4d1c-9c7d-5af5515a1e6f	Bužim	buzim	77245	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2feed7c1-6468-4334-bbac-24d907c47a66	Gračanica Selo	gracanica selo	75276	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
8f71a58d-ee0e-4ddf-be53-14d34c1f90a7	Divičani	divicani	70204	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
a73ace81-c0f5-4b1e-8604-33dba4b37030	Janja	janja	76316	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c08af0c6-d0f1-4851-826e-dd9282de00c7	Brod	brod	73309	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
241522bf-97aa-4a94-9f43-aae2d145586a	Kraljeva Sutjeska	kraljeva sutjeska	72244	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1cb7c70f-bbeb-43f4-ba77-7467418c5fb5	Bosansko Grahovo	bosansko grahovo	80270	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1ca7046e-b5ed-49cf-8535-f06692d876a2	Konjodor	konjodor	77249	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1e0c63fc-0097-4233-9b6a-d81b0d5646a8	Novo Goražde	novo gorazde	73110	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c8e13bbb-0572-4aba-aff3-72150b9b189f	Boračko Jezero	boracko jezero	88405	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
109e7375-5267-4ff9-8cce-48597f651d78	Dubrave	dubrave	78411	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3192169c-0b8c-47e5-ae21-73de1331b27a	Hutovo	hutovo	88394	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1e3b2a28-aeea-469d-9946-6f956b132042	Lamovita	lamovita	79204	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
58dc3fdf-6c7a-4eec-b7c9-e4acefc0d6fb	Glogošnica	glogosnica	88422	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
58dc2aae-405d-44d2-9ada-151e19b52355	Haljinići	haljinici	72245	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
74d6a147-0d94-4702-be7d-4bff85bfd56b	Mala Sočanica	mala socanica	74418	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
87c8b350-aebd-4985-9180-edd62c9924a2	Crkvina	crkvina	76239	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5a90de85-eca5-471f-b9b0-bad2dde308d2	Brčko	brcko	76120	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
866eee53-1605-437d-8ba2-6bb630bbeeed	Kalesija	kalesija	75260	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4b94a713-501a-40d1-9323-6a8464c1ca28	Milići	milici	75446	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
81baf607-0a77-4aba-a63a-7bd0b1d33d45	Lepenica	lepenica	71254	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5051cb94-8cd2-4422-bae5-4961a16a0245	Omarska	omarska	79203	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6cb5c94a-4718-44cd-9ee6-1bceea4270af	Goražde	gorazde	73101	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5c6f1c78-ebb8-4664-9d48-ff88e2f5b00b	Gornji Ribnik	gornji ribnik	79288	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5912d421-767f-41f0-8f37-dcb08ffa092f	Dužice	duzice	88342	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
94f33777-78e5-4124-85a5-23beb339fca7	Kifino Selo	kifino selo	88283	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2e235491-a3f5-40d3-8d4f-b40ecc9102cc	Bihać	bihac	77000	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
71b84929-e060-4a49-83d4-5514e87ad083	Dobretići	dobretici	70210	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
90ea3112-f356-4fb0-bb6e-9c9a86793a3f	Paprada	paprada	75453	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3753259d-372b-4739-b93a-e5d06ee11d12	Knežica	knezica	79246	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
06f31d21-2b28-4fa0-8334-7ddfca61086b	Matuzići	matuzici	74203	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c43fd6ff-7eca-4b3e-a9b7-d7a3cf878b40	Nova Bila	nova bila	72276	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5865474d-e25d-4cc2-b137-89a4069536f4	Fajtovci	fajtovci	79264	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
844845b6-9df7-4c34-8cc9-bc55e0dcfb5c	Orahova	orahova	78406	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e51ef3ed-cb20-4b69-b2f7-0e21568ff785	Dubrave Gornje	dubrave gornje	75273	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
79649a91-d12b-48dc-8de2-dcac11d4e24b	Kozluk	kozluk	75413	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
84b0a059-556e-4a7e-a934-b4dacbdf1f8d	Malešići	malesici	75326	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5b309b45-8097-4936-bdea-0cbc4594132b	Jelah	jelah	74264	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d32a15f5-5556-4379-9a77-6b82633c1910	Pale	pale	71420	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6d25869c-7d46-4384-919b-2ee7084d7cb9	Bjelimići	bjelimici	88407	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ce4b5cd0-8ddd-4bd3-b306-9d6157e9d34c	Kladanj	kladanj	75280	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1604afc0-e6b4-49e9-a002-070e2b58d960	Banovići	banovici	75290	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
440cbadf-d524-4648-aa97-dee7f5f1bd3b	Osinja	osinja	74412	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9b32d197-0e78-4b06-a663-ff4c0822d44e	Foča	foca	73302	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2aa74630-6436-4874-88e2-4136ff513e63	Ilidža	ilidza	71210	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c11d45c7-f19c-47c2-b3d4-6a4f86586ef6	Kotor Varoš	kotor varos	78220	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
753b5fe4-6fdb-4f89-acff-903feb9e168b	Gornja Koprivna	gornja koprivna	77222	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
8303ec08-fcea-4d62-931d-dcdfb946a42b	Kobaš	kobas	78423	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7d8f857b-8739-4c8b-8b56-9a9ee7aa821b	Kotorsko	kotorsko	74215	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
242fd773-ee4c-4020-aecd-83e975731f3c	Brka	brka	76206	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
fce3ca66-3244-4d6b-bc59-129bfeada609	Brnjic	brnjic	72243	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3698d2b5-7b62-4811-bede-fc04c57742af	Orahovica Donja	orahovica donja	75323	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
43a2c95d-37d7-43a1-901c-9f187b7bb28e	Grabovica	grabovica	74219	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
05587174-d913-4821-bcc2-0610391f625b	Kalenderovci	kalenderovci	74413	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3e3c1a6d-f858-4528-ab08-b3ab7fc1215c	Hajdarevići	hajdarevici	72225	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2cd4ad17-4f41-4e58-8a4a-9a2f47b8ef19	Neum	neum	88390	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0833843d-2a3b-4c95-bb31-7c3ac07e41f2	Begov Han	begov han	72233	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e0051ea2-2993-4c50-97de-db3cee64c97d	Jajce	jajce	70101	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d8bb2572-0d80-4371-8fed-0c71d83625bd	Donja Mahala	donja mahala	76274	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3436c95c-4880-4630-bc43-9091f2de0590	Garevac	garevac	74484	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
eb77e33f-7ecb-4e03-9822-903f96fdd1c2	Borike	borike	73225	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f227f6c6-7002-478f-a9bf-9f473079abf1	Novi Travnik	novi travnik	72290	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f94d48f4-a697-476d-84f4-4f0a6fdd70f2	Kornica	kornica	76236	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
76fdd2f6-5347-4c0a-95b6-a045b04cb8e0	Bilalovac	bilalovac	71253	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6f0b60bb-bc55-4501-bf71-a18ef5bd5818	Ljubomir	ljubomir	89209	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ccd432a8-d7ad-4c03-b714-97977bee2ea3	Bode	bode	76210	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
93760c4f-dde1-4a9c-a2b2-796b64a0f764	Baraći	baraci	70267	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c901787d-8573-4e44-98ef-653319a8f54a	Dragočaj	dragocaj	78215	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1a0052c2-cd56-4992-be1b-34d101f3bf5a	Batković	batkovic	76312	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
387e2703-d50d-4a7b-aeb4-9cb2fe0584e9	Čelić	celic	75246	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0499b793-8786-4c71-8e50-c0ebac73589d	Čitluk	citluk	88260	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
8053ad82-e74f-4423-bab9-633394e25704	Blatnica	blatnica	74275	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
49eb755d-e67b-4446-a37c-a4c8cfb630d5	Bijela	bijela	76204	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
84fe4df7-600c-49fe-aa3f-2d33eb2a04c1	Maoča	maoca	76208	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
69d68b32-a4b0-4e39-9ed9-9d62c9f71db0	Đurđevik	durdevik	75272	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
83c6b18c-16bf-4173-8fc6-bea74f6d5316	Kostajnica	kostajnica	74222	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ded47a30-cc16-498f-833f-9b34f5ee54e1	Lastva	lastva	89208	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c7f42c8f-2c8e-4f6c-bd88-460dcc0ba440	Gračac	gracac	88443	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
36c9f842-c92d-44d0-bc09-03d7feb1e5b1	Ozimica	ozimica	72238	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9c616845-8687-4226-9c5f-10d12927a644	Bijeljina	bijeljina	76000	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
db58f52c-320e-4763-9cae-28f87d7a19b7	Lončari	loncari	76278	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0ee67ed1-1e44-4e3a-b324-f07fdd1d05a5	Jezerski	jezerski	77241	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e8d781e8-f9ea-4536-8b92-68e84a40f035	Lipnica	lipnica	75213	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
fe69b2fe-2a55-4df1-a5c1-e9d47893ea73	Modriča	modrica	74480	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b83eed62-c8ab-4672-b591-674a8bce8e07	Kosova	kosova	74253	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
76207293-b35e-4df8-852b-ab7adaa00a41	Brezičani	brezicani	79208	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5e7d60e1-e878-41c7-8136-ac0a8acd91f6	Pazarić	pazaric	71243	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3e146fda-be3d-4d39-9d0e-c340bf9add65	Mramor	mramor	75212	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
99cae3d9-6a50-40e7-82f6-5a9f2ca64c93	Ključ	kljuc	79280	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3a8f5d7b-796b-4c65-abd2-9aeff024fee2	Dubovik	dubovik	79227	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2a4e7de8-04ba-4686-be7e-af0c4247647c	Gornja Slatina	gornja slatina	76238	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0e99b74c-3ac1-4e16-b502-8b9b4518a343	Kuljani	kuljani	78212	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
796d9d2f-d365-4300-89a1-857d1c59a010	Gabela	gabela	88306	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9c69f90a-4d98-4807-ba24-00b335eb476f	Hadžići	hadzici	71240	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
897cb1e6-5683-43d8-8888-445ccd73c5c1	Doboj	doboj	74101	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b4856b8a-88dd-4c54-884c-0e37692440b7	Ljubače	ljubace	75214	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9aa39921-28ab-4eb7-948b-d9a618351bf9	Dugo Polje	dugo polje	74483	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3617f849-379f-4fd1-912d-a22082219c6e	Koprivna	koprivna	74489	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
92a22cf6-b2a1-4a31-a49a-19f474ecef93	Crnići	crnici	88367	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d3ea7594-c8bd-460a-8ee3-9f95aadc7526	Laminci	laminci	78407	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e8540f74-1c2b-4985-b944-adefa736b35c	Orguz	orguz	80206	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
29bc06ef-c127-4b07-b95f-76fe3312bc2b	Banja Luka	banja luka	78000	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
572479b8-3df6-4202-8e32-5601c630f0b6	Caparde	caparde	75405	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5bbbdcad-fb6d-4d12-9995-773f007fe347	Paklenica	paklenica	74255	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
70d4add2-641d-48aa-b5ac-2ff28c796f43	Karadže	karadze	70235	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
10975247-b068-4879-8877-8e61970a2547	Bosanska Krupa	bosanska krupa	77240	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
456f2ef9-9d5d-48ba-90c8-1b749162cb8e	Gojevići	gojevici	71275	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7589f036-7cd7-4eb5-a4ba-afd0c4bfe8ea	Ljubinje	ljubinje	88380	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0381f77d-c7cf-48e8-90a4-6c3a4de61940	Drežnica	dreznica	88215	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e91cc073-2b58-4e2b-bfa7-ceef881d9b6e	Dolac na Lašvi	dolac na lasvi	72278	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
84857acb-38e5-40c4-82ca-a93707803a34	Kulaši	kulasi	78443	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d4f1efb1-297a-429e-9024-a6d4081f758b	Goleši	golesi	78203	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3cf2222a-bde8-4d8e-8558-b20e083de0f0	Mala Kladuša	mala kladusa	77235	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
bd03f1b2-5734-4d6a-89e3-1974e72bdb03	Odžak	odzak	76290	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3797c460-e03a-4f3d-9057-661984f4856a	Osmaci	osmaci	75406	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
bb38113d-49c7-4519-8b92-82f5376b19ed	Nožičko	nozicko	78428	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0b08dac9-3167-4d1a-83b2-7ccde2fc5605	Careva Duprija	careva duprija	71347	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d7f86837-36df-44a9-a6c0-5d33bfb9d890	Bileća	bileca	89230	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
8a695d74-97f2-4d0d-9fa0-41557d9eb182	Nemila	nemila	72212	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e847f308-bcad-4928-905d-f2a010f250b5	Hreša	hresa	71144	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
58118c01-8de8-4b59-aebe-92116346df9f	Gračanica kod Bugojna	gracanica kod bugojna	70233	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
cbd5ccd9-c7e3-4041-aadf-2950b2c9cd33	Mostar	mostar	88000	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2e43978f-f687-4435-9675-06e11a21c83b	Donji Vakuf	donji vakuf	70220	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1efb8c48-72c1-4c74-9af9-6334cab36945	Ilovača	ilovaca	73208	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e9e8c733-7a72-46e4-90cc-c391a5011afd	Čerin	cerin	88265	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d7251a76-a8a6-4347-983f-26f23115507c	Kočerin	kocerin	88226	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4b8be710-4cad-4acb-9f38-fab92c0ee44e	Gornja Tuzla	gornja tuzla	75208	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
49ba5697-cf58-481b-88d4-b221027a3eb7	Javorani	javorani	78233	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c8cd4bfe-354f-4c86-8b1e-4b7243351349	Glavatičevo	glavaticevo	88406	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
78a5c6e4-08ee-4f1e-8178-650c8fda08a4	Domanovići	domanovici	88305	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2e4e9c7f-a646-476d-8c57-ec93d5983fee	Imljani	imljani	78234	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2310a702-c25c-4bc1-8c44-55076189b646	Krepšić	krepsic	76212	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4b5835da-8183-45b0-a157-c10b30feea6a	Miljeno	miljeno	73283	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3f59e45f-36bc-48a3-aa07-5e02aed38e71	Donji Kamengrad	donji kamengrad	79266	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
fdcfc2a0-d49e-48f0-8659-7631615bbf08	Crnjelovo	crnjelovo	76328	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4ecd01a5-0b11-4108-9183-4184b34612ba	Bukinje	bukinje	75203	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
fa165468-ce3f-4e6f-b8d0-054e9c88fb9c	Klobuk	klobuk	88324	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
69d35676-6e2f-415f-9868-e4258e4399fd	Karaula	karaula	72284	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4a0adbb6-4735-4c3d-a528-3ad184b2ece6	Bila	bila	72256	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
bb5a4640-bbdf-4bc1-80c9-c72e44974df3	Mrkonjić Grad	mrkonjic grad	70260	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1b0cb44b-7d13-4887-8379-b59167983214	Jošavka	josavka	78244	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3ccf76ea-8684-49cb-acab-f314b89effc7	Glamoč	glamoc	80230	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ee7e072e-f598-4506-915d-82d27f10b925	Miričina	miricina	75329	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5f7b458d-8630-453a-a7a0-3df7e7f28f4c	Dobrun	dobrun	73247	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
afb51ca4-a7fd-47a2-913a-507aa961ea42	Dukali	dukali	78427	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
81ed81dc-42fb-4387-ad2f-39ccf597d4a4	Gradačac	gradacac	76250	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7bbf87a5-f1fa-44ac-990e-075ad0351a35	Donji Žabar	donji zabar	76273	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
026c1a80-98d1-4ab3-a503-55597ae63135	Drinovci	drinovci	88344	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
fc4cc251-7d72-404e-a299-be03e0f43855	Ilijaš	ilijas	71380	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
917ef2d1-ea7e-4298-baa3-2a324a7f3a93	Drinić	drinic	79290	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
cde02b04-4fd3-4d44-be16-b9a16d7ed12c	Kulen Vakuf	kulen vakuf	77206	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
cbf5d242-0924-4852-a04f-2d6edd611e76	Kola	kola	78207	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
15af05da-14be-4abb-b2be-07fa8e55daab	Han Bila	han bila	72281	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
cc0ece4b-664e-4db3-9d4b-aaf9c9c87bec	Klokotnica	klokotnica	74207	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
fd5c9b08-b353-43c6-9425-c2fe16fa7434	Knežina	knezina	71356	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
fb921b81-4cd2-4e08-9985-e47e4c686f54	Breza	breza	71370	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6bcf384b-7904-4b67-a6a5-3180ebfc148b	Jare	jare	88224	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
82847b71-81dd-4e99-ad6f-a0c957c03dd9	Duboštica	dubostica	75308	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
68b2ef88-f006-4955-aea0-c52b2adba77e	Donja Međiđa	donja medida	76257	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
772a862d-d4d4-4766-a163-d9963325b501	Lukavac	lukavac	75300	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4458c712-3c7c-40c8-89b0-dd8427ccd2ab	Osječani	osjecani	74225	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ac3b22a6-8efa-415c-b734-c7c73b29cc07	Olovo	olovo	71340	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
53b70280-7e3c-4d80-a8b3-ed12bf01ae0d	Lušci Palanka	lusci palanka	79267	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
909e108b-19c2-47e5-bd79-a579ef9e0f71	Ljuti Dolac	ljuti dolac	88223	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
37b4d713-ca96-499f-b44f-ec73da2fcbfe	Laktaši	laktasi	78250	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e915b8ce-8a21-4a47-aa9c-9b9f6d3e4e82	Grude	grude	88340	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
a3e40ef7-567b-411b-b808-68632aea19fe	Kukulje	kukulje	78424	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b1ae1238-6738-4a83-850f-7cb79f53e32e	Kozarac	kozarac	79202	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
02cf7dba-04aa-4b82-b2c5-cdec13955532	Brijesnica	brijesnica	74206	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
bc96d046-b994-4080-afc6-b38c999ab2c5	Divin	divin	89233	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
a2acc855-f435-4bdc-9be4-5e0a30ffe3c5	Doljani	doljani	88446	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
26cf1934-bdf2-4cc7-bf0a-8483d47371b8	Bronzani Majdan	bronzani majdan	78204	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9f5a7593-570c-47e5-9e7a-83b5432b9d7b	Buna	buna	88202	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c96e1451-7d2b-4820-9d0e-bbe2cceb42d3	Opara	opara	79293	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
93507cbf-4bf9-4ce8-b260-80fccdb588dd	Fakovići	fakovici	75423	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3da65c23-f6e9-49eb-b44f-7bed8905e6be	Donji Mamići	donji mamici	88343	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
275b33de-703a-468b-91ef-cb40d18d97a8	Mokro	mokro	71428	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1ea016c7-539b-49e9-aa90-ec8d453cfe6a	Gornji Rahić	gornji rahic	76207	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3c4b4ca0-8b6d-4597-8b95-d991653cdbf6	Ljubija	ljubija	79206	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1ac3236f-e59f-4f0d-b50b-50b51a7f6c5a	Guča Gora	guca gora	72277	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1543ddc7-0783-4eb2-bb84-d407829d110b	Brodac	brodac	76313	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
48c23d86-56f4-4e20-a97a-c3c2e0e33cd0	Otoka	otoka	77244	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
62682bcc-b776-47bc-999e-d90946b3d3b5	Kreševo	kresevo	71260	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
162bbfa0-4160-4a96-9f93-cf981d6908f7	Kamenica	kamenica	77204	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2ea88bbc-3761-4692-aa2d-afb1cb965109	Kriškovci	kriskovci	78256	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
337bb814-5e95-4db8-9781-20ae29072d29	Mravinjac	mravinjac	73206	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
28eea40d-9a7f-4efe-bcba-ebbc9404ff1a	Dragaljevac	dragaljevac	76323	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5afe8271-48ef-4031-ae1a-0d2dcb2a14ae	Dragalovci	dragalovci	74209	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
8f33fa07-897b-46d0-ac08-e486d8ef8994	Oborci	oborci	70225	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1eac605f-b990-4505-8dd8-436627e92cbf	Kovači	kovaci	72226	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
70184109-7b2c-4a5c-afad-6921caf2c79c	Nevesinje	nevesinje	88280	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
af9396d0-d9ef-410a-af69-e62bc9e0027f	Izačić	izacic	77208	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
68d5c0c2-cede-4ae1-adf7-e1be9cf0a2a1	Delijaš	delijas	71223	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ed5563ca-01bb-4600-bd2f-a5f833bad79d	Pećigrad	pecigrad	77227	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
65d07bdc-b477-436a-a73c-c5a9629e7afa	Pelagićevo	pelagicevo	76256	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d8125a40-6367-4847-8561-5cb69a18cd2f	Petrovo	petrovo	74317	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e642577f-9212-427e-835d-22cfbcbf44ba	Pilica	pilica	75412	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b9e158d8-f622-419c-947b-9596a5ac43c4	Piperi	piperi	75248	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
8c379e85-e59a-420f-aece-28c8cbb2b1fa	Piskavica	piskavica	78217	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d1471f9c-9ddf-473e-8520-2f69c8b32439	Pljeva	pljeva	70275	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3a0a394a-ca4b-4341-b6d9-c61ef9971ee0	Počulica	poculica	72252	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
48d0caf7-9c53-44d7-b5f6-556102f90fec	Podgrab	podgrab	71425	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e914e681-17c5-4e07-a802-61aea49e9072	Podhum	podhum	80209	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
356ca81e-4941-4df5-bbef-a1f51d606d14	Podlugovi	podlugovi	71387	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b9777fb1-6bdc-4ab4-9ead-781977c28ff9	Podnovlje	podnovlje	74217	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d5f0243e-cc72-4226-995b-703f50f7064e	Podorašac	podorasac	88403	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3635c78b-a5bb-481a-b4a6-2cb5aba4b240	Podorašje	podorasje	75355	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
35728dfd-c3f2-48c4-9798-c299f3843345	Podrašnica	podrasnica	70266	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c4a60c69-0af0-460f-9969-e7836da65b3b	Podvelež	podvelez	88206	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d9b32fd2-fdf0-4573-b492-499d7d3a32cd	Podzvizd	podzvizd	77232	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
a0e61db8-75e7-44c6-8162-a088d54264fd	Pokoj	pokoj	77209	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b22929f8-a180-48b3-8fc3-c1844a26b0dc	Poljice	poljice	75303	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
78484a24-b63e-4677-a6bc-7972b29a9392	Posušje	posusje	88240	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9b4bef05-9e85-499e-9c82-8bf908cbc757	Potkozarje	potkozarje	78216	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
94dbb89b-b2a1-4c33-9e4c-798b1c7bbb73	Potoci	potoci	88208	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4fa24304-e1b7-40bb-80f5-e5b511690480	Potočani	potocani	78435	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7891ddaf-12e7-4d7b-8f7f-d68940e721c2	Potočari	potocari	75433	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
784c5db8-6177-44c0-bbde-dba71c157f1f	Prača	praca	73290	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7c7474d6-ffef-40a2-9cfd-b696446768e2	Prelovo	prelovo	73245	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
53c761fc-c04a-4eaa-83cc-1e7c29b3794e	Previja	previja	79287	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6025a109-20f4-4758-b9ed-696c86c86290	Pribinić	pribinic	74276	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
8e8a572f-f3f7-49d4-940a-28990016df8e	Priboj	priboj	75249	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3b163f7f-eb3d-4ffa-8283-a466c8895dd2	Prijedor	prijedor	79102	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
857cec72-19b3-40da-895b-497db5a2625d	Prisoje	prisoje	80245	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0d2e311a-4bbb-45e1-a81f-e5e5be813aa7	Prnjavor	prnjavor	78430	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
31709ecb-c269-4a61-8fc7-f5ff1d23df79	Prnjavor Mali	prnjavor mali	74214	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f85aac6e-9719-4c03-94fc-8583b760be4a	Prozor	prozor	88440	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
74e1579e-7e16-469f-ad13-9de44e95cf12	Prud	prud	76292	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
577229e9-7fa4-453a-af69-704e44208004	Prusac	prusac	70223	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e4a216f3-aa06-4021-9490-0a517e4142f1	Pržići	przici	71335	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
99c951cb-58d2-4fa1-befb-18880d99b394	Puhovac	puhovac	72207	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c517ef51-43bd-4456-b5b1-10c0e609a6a6	Puračić	puracic	75305	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
85e2d203-8fa2-41f8-90af-3707e701faf8	Radišići	radisici	88325	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3f37bfbc-d1d3-4345-ab43-906b1b692119	Rainci Gornji	rainci gornji	75268	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c405ff67-2c73-4489-ac34-3b4ae13db4a8	Rakitno	rakitno	88245	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e8e42c88-afb0-4887-8d73-c463787ccc02	Rakovica	rakovica	71217	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f492b5df-e9da-4a42-855e-bdd51609ccb2	Ravno	ravno	88370	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
49b64967-43a3-477a-8729-5080578ac4e4	Razboj Lijevče	razboj lijevce	78249	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
bd57d934-104b-4418-bf61-8cb29a2e4372	Ražljevo	razljevo	76218	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d0babad1-8c10-4ce9-b225-d827130fb8cb	Ripač	ripac	77215	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5a6f628e-4a1d-4668-83c6-d3138d83ae82	Rogatica	rogatica	73220	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e90435f8-5129-4e03-bdfe-671f3169ecfa	Roško Polje	rosko polje	80247	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
a33ea281-274d-4547-ada0-efa972b5eb00	Rudice	rudice	79226	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
af628185-2ba2-4d2f-a423-bf66620942eb	Rudo	rudo	73260	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5d9cdd05-4b06-433c-8223-4a6b7c9d929a	Ružići	ruzici	88347	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e9c03417-a8ad-4d34-acdb-b65395dbb88a	Sanica Gornja	sanica gornja	79285	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c1e2b91f-3be1-479c-8fde-d6d20ae4d0c4	Sanski Most	sanski most	79260	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d73447f8-3c41-4fd2-970c-4b91e4971093	Sapna	sapna	75411	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
14cbc0d6-8590-44f6-9fd4-fcd9f9a9a72b	Saračica	saracica	78202	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
42f01768-c3bd-4d85-a793-42e765425f36	Sarajevo	sarajevo	71000	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7da5f0e3-a004-4b6a-8bce-496b6c951705	Semizovac	semizovac	71321	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
61ef928e-b50d-4077-b2a3-9a4db599834d	Seonjaci	seonjaci	76205	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
cfa4c2b6-d439-4d82-ad08-7806de83fec0	Simin Han	simin han	75207	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9e63a93f-b35d-4ef2-aafa-99aae180c327	Sitneši	sitnesi	78422	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7d91163c-fe01-4d68-9210-5f2ffb83d418	Sitnica	sitnica	79283	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
caa3a9a6-f821-4ac3-87a2-7e78a7183a97	Sjenina	sjenina	74212	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5acbbf9b-19df-4287-b37c-71444809f7d2	Skelani	skelani	75436	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
15c213d4-7bb6-45b1-9368-626ced8fbbc1	Skugrić	skugric	74261	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6bda12e5-fe6c-4d49-ad00-6e75151ee3c4	Sladna	sladna	75353	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2e4a46f9-2ee7-4b79-9d53-1f75b371a923	Slatina Ilidža	slatina ilidza	78253	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f45b371b-34ca-4326-bfa0-a31a25e81937	Sočkovac	sockovac	74323	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
76770983-1424-4853-bb36-5954d0dfecf3	Sokolac	sokolac	71350	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3e34d004-5618-4b1b-bf73-6fa22570f9d7	Sokolovići	sokolovici	71357	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d03aa991-210c-4cd4-a187-d1358fd7b4cd	Sovići	sovici	88345	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
376fa6be-9ce8-4054-a4cc-f39eb4f3e0b1	Srbac	srbac	78420	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
a2aca97d-b4ab-4eb2-aade-5fa4233eb95a	Srebrenica	srebrenica	75430	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1da78c75-6a75-4a1b-a0eb-490439379445	Srebrenik	srebrenik	75350	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ca0d4faf-a372-4465-8c25-7fc928a13b99	Srednje	srednje	71385	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
11f8e43b-fe2b-4947-bfc9-8ab613549385	Sreflije	sreflije	79249	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3ee993d4-0e7a-425c-850c-671f481c0348	Stanari	stanari	74208	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
78c0f36d-30b8-407e-a976-09ca53e11785	Stara Dubrava	stara dubrava	78243	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3eb39450-a46e-4a21-aaad-b0b553aace21	Stari Majdan	stari majdan	79268	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2cce746a-c085-4e75-b476-3a16ccf54979	Stari Vitez	stari vitez	72251	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c8705b49-fc1a-4da7-a3e9-930824ef08c4	Stijena	stijena	77224	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ef53354c-fab6-4025-9ffc-140e47a257de	Stjenice	stjenice	73223	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9fd166ce-e569-41a9-b978-7fc64c74d573	Stjepan Polje	stjepan polje	75324	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
350a50fe-85cf-424c-b147-c419b4faf18e	Stolac	stolac	88360	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d0f101f2-dbd3-40e7-98d3-9fb998db6f8a	Stranjani	stranjani	72209	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
20c050ed-e052-4778-851a-fd5bb601139e	Stričići	stricici	78208	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
99efd3e4-44c4-4d39-aad2-cad0b52f6a6c	Strojice	strojice	70273	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
80254194-10c2-4595-be77-1a3a729b0686	Studenci	studenci	88323	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
522ee60c-d93f-43a8-a5a5-f207d83c54de	Stupari	stupari	75283	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
752b8ae5-6759-44b5-bc17-c29b2acaf202	Suho Polje	suho polje	74221	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f3d17a53-b3d0-4939-97c9-14aadbadf215	Svodna	svodna	79229	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
eaf37c98-5e4e-46f2-bd9c-a13d9b3d5298	Šamac	samac	76230	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0547f51d-23d4-4b1a-9a0d-d8dcacb0b80b	Šatorovići	satorovici	76209	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
dff6baba-2bfb-40b8-aedd-9c51cc804fd3	Šekovići	sekovici	75450	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
17f66e44-bbdf-4222-aded-ded78803fa85	Šerići	serici	75275	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3f6ff43d-4770-4c82-a490-0f0a5d82e6a3	Šibošnica	sibosnica	75245	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3e7a2a4a-b72a-4c4e-a3bb-d2a630749440	Šibovska	sibovska	78433	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
26caec8e-ec56-48c2-8086-c475381ee963	Šipovo	sipovo	70270	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5dcd8b59-5884-4e61-85fc-bf2a9965de97	Šiprage	siprage	78224	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
71adcb0d-1811-45fa-b69e-ae5aa24b5b02	Široki Brijeg	siroki brijeg	88220	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
a2b9cdc3-f9e8-4ed8-b0bd-fcd6bae823ff	Šnjegotina Gornja	snjegotina gornja	74279	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b0ce66d5-0b67-4322-9bb6-cced24d09cad	Špionica	spionica	75356	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
870ec120-0388-4e37-8af3-7fe4012df868	Šturlić	sturlic	77223	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
201a3103-20e2-4ed2-8aa4-acc00bb3ecec	Šujica	sujica	80249	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
22dfd38a-f0d1-4b2e-9c3b-de1b5a90ed3e	Šumatac	sumatac	77234	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
fb157fd7-67d7-4d2c-b360-0547d51dee71	Tarčin	tarcin	71244	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
89db9d62-3418-4820-af79-b11c9d257be2	Teočak	teocak	75414	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
48d65657-f742-43ac-83ff-b14cd11630bd	Teslić	teslic	74270	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1ce90703-a5b0-42a6-b440-7c8746261f9c	Tešanj	tesanj	74260	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1307353c-6d2a-4d5d-9618-535d0e80d0ac	Tešanjka	tesanjka	74266	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
25521e04-bb65-4b49-8165-244eaec117f1	Tihaljina	tihaljina	88348	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
81964645-59df-4c5a-8bec-7b21c005e56a	Tinja	tinja	75357	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5d28a416-e963-40d1-9927-4a71dda6e194	Tišća	tisca	75455	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
862c2010-262a-44b2-9884-a54e7e13c33e	Tjentište	tjentiste	73311	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
58553f23-b75d-475f-a048-178dd0f9fa6a	Todorovo	todorovo	77233	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
90e11041-7b2e-4d66-afef-f369a43e2f11	Tojšići	tojsici	75265	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0f670756-77cc-4132-8517-fdbeea34593b	Tolisa	tolisa	76272	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
bf03d127-e3b4-447e-8bf0-3db4e967d545	Tomislavgrad	tomislavgrad	80240	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ae632cc4-035d-4072-85b2-bcffb573c334	Topčić Polje	topcic polje	72213	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3d353e2a-e1b9-4ddd-ad6b-efe567f8fae5	Torlakovac	torlakovac	70224	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d6368a39-5ea2-438e-b33e-ed4302fcae5b	Travnik	travnik	72270	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b8e6ccf3-01b3-40e0-aeef-ce5d1a578e92	Trebinje	trebinje	89101	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
aa3f9032-925d-463e-8de4-6c92c36080ff	Trn	trn	78252	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
59c76d24-c346-4dca-b8ee-366161c49043	Trnova Donja	trnova donja	76335	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
262320b1-39ec-4644-9514-368decd5dcb4	Trnovo	trnovo	71220	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3d890628-e352-4795-b909-86b115c0cf15	Trnjaci	trnjaci	76310	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e8acb732-3a1a-409f-a083-c8094fa4dc25	Tržačka Raštela	trzacka rastela	77225	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
bf9a7168-5a7b-4eff-815c-4594a9fc0aa6	Turbe	turbe	72283	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
97236469-13db-4ca6-92b8-7acde9a21396	Turija	turija	75306	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7b082b7f-c784-45d0-a992-0d8768a8fbd8	Turjak	turjak	78404	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b2c858ec-73c0-4798-80c9-871ffb5ba47e	Tuzla	tuzla	75101	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
29824940-e862-4483-959f-fbb97e799514	Ugljevik	ugljevik	76330	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
64b0bc0b-6c56-46fc-8c19-ebb7e2130740	Ugodnovići	ugodnovici	74278	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
94d0f4f0-608b-474b-b393-8e5fe6c45f3c	Uskoplje	uskoplje	70280	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1d01e9c7-8b22-4a33-ad10-793ae5b62546	Usora	usora	74230	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c1a8e24f-b560-43ac-869e-d66746976ccd	Ustikolina	ustikolina	73250	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3214ec5c-c433-46f2-842b-c3347de043da	Uvac	uvac	73265	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
d5e6b00e-e40d-48e0-a3ca-b68b1824d52c	Uzdol	uzdol	88444	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3c5734a6-8335-488a-a292-a931b78b9586	Vardište	vardiste	73249	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4caeafb8-5e7f-4a01-877c-efea7f0223d7	Vareš	vares	71330	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4ad605e7-5d36-4953-9861-2a8f74a5baf8	Vareš Majdan	vares majdan	71333	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c096ca10-6816-49ec-a797-e15002e226cb	Varoška Rijeka	varoska rijeka	77243	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
9814cc81-05c1-46d2-86e6-93cca4d06917	Velika Bukovica	velika bukovica	74213	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ec306e87-04ab-4b4e-af68-20e894bd0cd6	Velika Gata	velika gata	77207	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5d8f965d-9ec5-4357-9ff4-6954c8794843	Velika Kladuša	velika kladusa	77230	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c55d12d8-56bd-48bf-aa38-1e74050b08e2	Velika Obarska	velika obarska	76329	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b0d3a5a2-9223-49ab-8c04-4e72ee9f0c7c	Vesela	vesela	70234	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0c910453-3e34-496d-9f8e-b1c4f60ea6e8	Vidoši	vidosi	80208	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
e6cf0615-9ecc-4165-a4e3-513749dff03b	Vidovice	vidovice	76275	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
c6e96c65-071a-423d-a66e-cf338b96ac4c	Vinac	vinac	70202	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
83388fe4-ec21-40a6-bb5b-918925acfa54	Vinska	vinska	74455	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
b9d17f2e-3e1a-42fe-a88c-796ddaf1256a	Vir	vir	88247	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2584f1d1-12c2-480a-94ff-3c9d5107a650	Visoko	visoko	71300	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0260ccfb-3dfb-4ecd-b845-c5c1ec736650	Višegrad	visegrad	73240	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
515102f5-ce56-4541-9020-4a3882524163	Višići	visici	88307	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6805ec44-baaa-4e39-aac2-f01cbd4c687d	Vitez	vitez	72250	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
2affcac0-4c3c-4898-be42-6f74c644f5e6	Vitina	vitina	88326	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1a840bcd-1081-46e9-b24f-f35d2fb42c0c	Vitkovci Donji	vitkovci donji	74265	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
67ca2950-b1ba-4f85-8067-b2d6cba88a14	Vitkovići	vitkovici	73205	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
281af4e8-ac13-43e2-8d5e-860e6c5ede10	Vlasenica	vlasenica	75440	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
3b26efbf-1bcc-489d-97ad-22252135e455	Vogošća	vogosca	71320	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f575c054-a541-4783-9a16-8a008a76c5f7	Vozuća	vozuca	72227	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
1e039316-24ac-40cc-b687-9156cefd628b	Vranjak 7	vranjak 7	74487	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
79826fc0-aeb3-4fc2-80e4-d75ffd3797cb	Vranjak 8	vranjak 8	74488	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
408caa95-dbc2-4832-ae4e-dffe2d8eda5c	Vrapčići	vrapcici	88113	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
8b742f8d-5544-47e4-8487-af3b85d644f4	Vrbanja	vrbanja	78211	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
28979c60-4533-4935-a487-fcdf3f90e9a9	Vrbanjci	vrbanjci	78225	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
f2619599-63e2-40b0-a5c8-f7d017d567cc	Vrbaška	vrbaska	78408	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
cb894ddf-e371-4028-950d-74ace11a2c6e	Vrhpolje	vrhpolje	79262	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
997c931c-f840-4c5d-abea-76f2b2488392	Vrnograč	vrnograc	77231	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
140d5eb5-9be9-4824-aedc-478b47d01d7b	Vrsta	vrsta	77203	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7d4add47-0f58-4bed-87de-790b479d07d2	Vršani	vrsani	76325	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4ec9212a-ee03-4172-85a9-dc9c494c49ba	Vučkovci	vuckovci	76254	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
6e258814-03bc-47a2-8384-f3c1c9a09a99	Vukosavlje	vukosavlje	74470	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
fae6ac58-599a-4452-931e-c5bb3b074428	Zaborak	zaborak	73287	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
45e64206-7071-46be-a82c-f0b72f84adf0	Zabrđe	zabrde	76333	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
109b6848-3ceb-4dc0-ba37-e0cc9aafebb9	Zalužani	zaluzani	78214	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7999be5f-f254-4590-803d-88aadada9a6a	Zavajt	zavajt	73305	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
8f477f45-d8c3-471f-a30e-1d6d2760aa15	Zavidovići	zavidovici	72220	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
bd7657b1-364b-4fd3-9f2b-bf1f589db8b1	Zborište	zboriste	74451	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
4e9b335e-fe2a-4102-ae3d-30f5bb441574	Zelinja	zelinja	76259	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
0c9144d2-c244-4ec0-88fa-13bd70b4d88e	Zenica	zenica	72101	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
ca820ff1-0e5b-4f2d-a6d9-6790f631904e	Zovi Do	zovi do	88286	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
7ff0fe0c-e37b-455c-b986-9d8d27553830	Zovik	zovik	76214	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
efedc20d-dbdc-4a07-9c2f-7e392f4463c4	Zvornik	zvornik	75400	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
5903325a-717a-487c-8246-ac55f6d3fc66	Željezno Polje	zeljezno polje	72236	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
021dcc8c-7019-40e5-b04c-e7020f3095d0	Žepče	zepce	72230	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
59374c25-805a-431c-af89-a7152d752b17	Živinice	zivinice	75270	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
14892074-fb6d-4cd6-a0ab-45de02e1257b	Župča	zupca	71373	Bosnia and Herzegovina	2026-09-25 14:23:25.147079+00	2026-09-25 14:23:25.147079+00
\.


--
-- TOC entry 3973 (class 0 OID 16533)
-- Dependencies: 212
-- Data for Name: countries; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.countries (country, code, created_at, updated_at) FROM stdin;
Afghanistan	AF	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Albania	AL	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Algeria	DZ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Andorra	AD	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Angola	AO	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Antigua and Barbuda	AG	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Argentina	AR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Armenia	AM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Australia	AU	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Austria	AT	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Azerbaijan	AZ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Bahamas	BS	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Bahrain	BH	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Bangladesh	BD	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Barbados	BB	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Belarus	BY	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Belgium	BE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Belize	BZ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Benin	BJ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Bhutan	BT	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Bolivia	BO	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Bosnia and Herzegovina	BA	2026-09-24 18:44:49.978093+00	2026-09-25 14:23:23.62542+00
Botswana	BW	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Brazil	BR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Brunei	BN	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Bulgaria	BG	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Burkina Faso	BF	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Burundi	BI	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Cabo Verde	CV	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Cambodia	KH	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Cameroon	CM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Canada	CA	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Central African Republic	CF	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Chad	TD	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Chile	CL	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
China	CN	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Colombia	CO	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Comoros	KM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Congo	CG	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Congo, Democratic Republic of the	CD	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Costa Rica	CR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Cote d'Ivoire	CI	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Croatia	HR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Cuba	CU	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Cyprus	CY	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Czechia	CZ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Denmark	DK	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Djibouti	DJ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Dominica	DM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Dominican Republic	DO	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Ecuador	EC	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Egypt	EG	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
El Salvador	SV	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Equatorial Guinea	GQ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Eritrea	ER	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Estonia	EE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Eswatini	SZ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Ethiopia	ET	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Fiji	FJ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Finland	FI	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
France	FR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Gabon	GA	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Gambia	GM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Georgia	GE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Germany	DE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Ghana	GH	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Greece	GR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Grenada	GD	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Guatemala	GT	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Guinea	GN	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Guinea-Bissau	GW	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Guyana	GY	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Haiti	HT	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Honduras	HN	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Hungary	HU	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Iceland	IS	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
India	IN	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Indonesia	ID	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Iran	IR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Iraq	IQ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Ireland	IE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Israel	IL	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Italy	IT	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Jamaica	JM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Japan	JP	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Jordan	JO	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Kazakhstan	KZ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Kenya	KE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Kiribati	KI	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Kuwait	KW	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Kyrgyzstan	KG	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Laos	LA	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Latvia	LV	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Lebanon	LB	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Lesotho	LS	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Liberia	LR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Libya	LY	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Liechtenstein	LI	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Lithuania	LT	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Luxembourg	LU	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Madagascar	MG	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Malawi	MW	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Malaysia	MY	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Maldives	MV	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Mali	ML	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Malta	MT	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Marshall Islands	MH	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Mauritania	MR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Mauritius	MU	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Mexico	MX	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Micronesia	FM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Moldova	MD	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Monaco	MC	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Mongolia	MN	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Montenegro	ME	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Morocco	MA	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Mozambique	MZ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Myanmar	MM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Namibia	NA	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Nauru	NR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Nepal	NP	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Netherlands	NL	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
New Zealand	NZ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Nicaragua	NI	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Niger	NE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Nigeria	NG	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
North Macedonia	MK	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Norway	NO	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Oman	OM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Pakistan	PK	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Palau	PW	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Panama	PA	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Papua New Guinea	PG	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Paraguay	PY	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Peru	PE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Philippines	PH	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Poland	PL	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Portugal	PT	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Qatar	QA	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Romania	RO	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Russia	RU	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Rwanda	RW	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Saint Kitts and Nevis	KN	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Saint Lucia	LC	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Saint Vincent and the Grenadines	VC	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Samoa	WS	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
San Marino	SM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Sao Tome and Principe	ST	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Saudi Arabia	SA	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Senegal	SN	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Serbia	RS	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Seychelles	SC	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Sierra Leone	SL	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Singapore	SG	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Slovakia	SK	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Slovenia	SI	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Solomon Islands	SB	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Somalia	SO	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
South Africa	ZA	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
South Korea	KR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
South Sudan	SS	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Spain	ES	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Sri Lanka	LK	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Sudan	SD	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Suriname	SR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Sweden	SE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Switzerland	CH	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Syria	SY	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Taiwan	TW	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Tajikistan	TJ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Tanzania	TZ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Thailand	TH	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Timor-Leste	TL	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Togo	TG	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Tonga	TO	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Trinidad and Tobago	TT	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Tunisia	TN	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Turkey	TR	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Turkmenistan	TM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Tuvalu	TV	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Uganda	UG	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Ukraine	UA	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
United Arab Emirates	AE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
United Kingdom	GB	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
United States	US	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Uruguay	UY	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Uzbekistan	UZ	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Vanuatu	VU	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Vatican City	VA	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Venezuela	VE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Vietnam	VN	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Yemen	YE	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Zambia	ZM	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
Zimbabwe	ZW	2026-09-25 14:23:23.62542+00	2026-09-25 14:23:23.62542+00
\.


--
-- TOC entry 4001 (class 0 OID 17533)
-- Dependencies: 240
-- Data for Name: daily_exchange_rates; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.daily_exchange_rates (id, currency_code, rate_date, rate, fetched_at, is_fallback, created_at) FROM stdin;
\.


--
-- TOC entry 3990 (class 0 OID 16979)
-- Dependencies: 229
-- Data for Name: dedup_ignored_clusters; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.dedup_ignored_clusters (id, admin_id, entity_type, cluster_id, member_ids, note, created_at) FROM stdin;
\.


--
-- TOC entry 3983 (class 0 OID 16852)
-- Dependencies: 222
-- Data for Name: email_verification_tokens; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.email_verification_tokens (updated_at, expires_at, last_attempted_at, attempts, user_id, token, id, used_at, created_at) FROM stdin;
\.


--
-- TOC entry 4004 (class 0 OID 17567)
-- Dependencies: 243
-- Data for Name: enabled_currencies; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.enabled_currencies (id, code, name, is_base, created_at, updated_at) FROM stdin;
a100ad64-26cb-4bb8-b911-a5a9a42c2de4	BAM	Convertible Mark	t	2026-09-24 18:44:50.199675+00	2026-09-24 18:44:50.199675+00
b31a4233-eb80-46ed-8901-d7a78e5e5d45	EUR	Euro	f	2026-09-24 18:44:50.199675+00	2026-09-24 18:44:50.199675+00
6ba6fe21-3c4e-4524-bdee-138b85f4be03	USD	US Dollar	f	2026-09-24 18:44:50.199675+00	2026-09-24 18:44:50.199675+00
6357c40e-9be1-483f-97b4-64c0dce63505	GBP	British Pound	f	2026-09-24 18:44:50.199675+00	2026-09-24 18:44:50.199675+00
28818b71-ee2d-45cb-acdf-59f67075bd35	CHF	Swiss Franc	f	2026-09-24 18:44:50.199675+00	2026-09-24 18:44:50.199675+00
d545bef9-615a-4349-88ac-a493fec7a92c	RSD	Serbian Dinar	f	2026-09-24 18:44:50.199675+00	2026-09-24 18:44:50.199675+00
c6e1fd05-21ec-409f-bc09-340deee9334b	TRY	Turkish Lira	f	2026-09-24 18:44:50.199675+00	2026-09-24 18:44:50.199675+00
\.


--
-- TOC entry 4005 (class 0 OID 17576)
-- Dependencies: 244
-- Data for Name: exchange_rate_fetch_alerts; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.exchange_rate_fetch_alerts (id, rate_date, error_message, fallback_source_date, acknowledged, created_at) FROM stdin;
\.


--
-- TOC entry 3981 (class 0 OID 16793)
-- Dependencies: 220
-- Data for Name: expense_categories; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.expense_categories (id, tenant_id, name, created_at, updated_at, exclude_from_reports, is_default) FROM stdin;
\.


--
-- TOC entry 4009 (class 0 OID 17755)
-- Dependencies: 248
-- Data for Name: expense_contexts; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.expense_contexts (id, tenant_id, name, description, is_active, created_by_subject_ref, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3989 (class 0 OID 16949)
-- Dependencies: 228
-- Data for Name: expense_items; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.expense_items (updated_at, line_total, alias_id, article_id, unit_price, expense_id, id, qty, created_at, tenant_id, price_modified, unit) FROM stdin;
\.


--
-- TOC entry 4011 (class 0 OID 17846)
-- Dependencies: 250
-- Data for Name: expense_template_lines; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.expense_template_lines (updated_at, default_amount, sort_order, article_id, is_active, label, id, created_at, template_id) FROM stdin;
\.


--
-- TOC entry 4010 (class 0 OID 17797)
-- Dependencies: 249
-- Data for Name: expense_templates; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.expense_templates (updated_at, recurrence_interval, default_expense_category_id, reminder_days_before, default_expense_context_id, name, default_currency, recurrence_frequency, next_due_date, default_supplier_id, created_by_subject_ref, status, id, default_payer_id, kind, default_notes, created_at, tenant_id) FROM stdin;
\.


--
-- TOC entry 3982 (class 0 OID 16805)
-- Dependencies: 221
-- Data for Name: expenses; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.expenses (purchased_at, updated_at, store_id, expense_category_id, currency, supplier_id, payer_id, id, notes, total_amount, receipt_id, created_at, tenant_id, rate_fetched_at, exchange_rate, original_amount, bam_amount, subject_ref, created_by_subject_ref, expense_context_id) FROM stdin;
\.


--
-- TOC entry 3999 (class 0 OID 17444)
-- Dependencies: 238
-- Data for Name: frontend_runtime_configs; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.frontend_runtime_configs (id, scope, config_key, config_edn, created_at, updated_at) FROM stdin;
8fddfb29-95dc-4f9d-a4ea-b6f30d1a5a49	admin	view-options	{:expense-contexts {:display-locks {}, :display-defaults {:show-add-button? true, :show-delete? true, :show-edit? true, :show-filtering? true, :show-highlights? true, :show-pagination? true, :per-page 50}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/expense-contexts.manage, :edit :expenses/expense-contexts.manage, :delete :expenses/expense-contexts.manage}}}, :expense-items {:display-defaults {:show-add-button? false, :per-page 25}, :display-locks {}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal}}, :suppliers {:display-locks {:show-batch-edit? false}, :display-defaults {:per-page 10, :show-edit? true, :show-filtering? true}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/reference.write, :edit :expenses/reference.write, :delete :expenses/reference.write}}, :column-defaults {}, :column-locks {}}, :subcategories {:display-locks {}, :display-defaults {:show-add-button? false, :show-delete? false, :show-edit? true, :show-filtering? true, :show-highlights? true, :show-pagination? true, :per-page 50}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/subcategories.manage, :edit :expenses/subcategories.manage, :delete :expenses/subcategories.manage}}}, :cities {:display-locks {}, :display-defaults {:show-add-button? true, :show-delete? true, :show-edit? true, :show-filtering? true, :show-highlights? true, :show-pagination? true, :per-page 50}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/cities.manage, :edit :expenses/cities.manage, :delete :expenses/cities.manage}}}, :admins {:column-locks {}, :display-locks {:show-select? true, :show-pagination? true, :show-filtering? true}, :column-defaults {:id false}, :display-defaults {:show-edit? false}}, :articles {:display-locks {}, :display-defaults {:show-edit? true, :show-delete? false, :per-page 25, :show-filtering? true, :show-add-button? false}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/articles.manage, :edit :expenses/articles.manage, :delete :expenses/articles.manage}}, :column-defaults {}, :column-locks {}}, :expenses {:display-defaults {:show-add-button? false, :show-highlights? true, :show-pagination? true, :per-page 25}, :display-locks {:show-batch-delete? true, :show-batch-edit? true}, :column-locks {:receipt_id false, :created_at true}, :column-defaults {:purchased_at false}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/can-write, :edit :expenses/can-write, :delete :expenses/can-write}}}, :manufacturers {:display-defaults {:per-page 25, :show-edit? true, :show-filtering? true}, :display-locks {}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/manufacturers.manage, :edit :expenses/manufacturers.manage, :delete :expenses/manufacturers.manage}}}, :unmapped-aliases {:display-defaults {:show-add-button? false, :show-edit? false, :show-delete? false, :show-highlights? false, :show-batch-edit? false, :show-batch-delete? false}, :display-locks {:show-add-button? false, :show-edit? false, :show-delete? false, :show-highlights? false, :show-batch-edit? false, :show-batch-delete? false}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal}}, :countries {:display-defaults {}, :display-locks {}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal}}, :payers {:display-locks {}, :display-defaults {:per-page 50, :show-edit? false}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/reference.write, :edit :expenses/reference.write, :delete :expenses/reference.write}}}, :categories {:display-locks {}, :display-defaults {:show-add-button? true, :show-delete? true, :show-edit? true, :show-filtering? true, :show-highlights? true, :show-pagination? true, :per-page 50}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/categories.manage, :edit :expenses/categories.manage, :delete :expenses/categories.manage}}}, :supplier-aliases {:display-defaults {:show-edit? true, :show-add-button? false, :per-page 10, :show-filtering? true}, :display-locks {}, :column-defaults {}, :column-locks {:id false}, :list-config {:form-display :modal}}, :audit-logs {:display-locks {:show-edit? false, :show-select? true, :show-add-button? false, :show-batch-edit? false}, :display-defaults {:show-batch-delete? true, :per-page 50}}, :receipts {:display-defaults {:show-select? true, :show-pagination? true, :show-add-button? false, :show-filtering? true, :show-highlights? true, :per-page 50}, :display-locks {:show-batch-edit? false, :show-batch-delete? true, :show-select? true, :show-add-button? false}, :column-defaults {:created-at true, :updated-at true}, :column-locks {}, :list-config {:action-gates {:add :expenses/can-write, :select :expenses/can-write}}}, :stores {:display-defaults {:show-edit? true, :show-delete? false, :per-page 10, :show-filtering? true}, :display-locks {:show-add-button? false}, :column-defaults {}, :column-locks {:id false}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/stores.manage, :edit :expenses/stores.manage, :delete :expenses/stores.manage}}}, :login-events {:display-locks {:show-add-button? false, :show-edit? false, :show-batch-edit? false}, :display-defaults {:show-batch-delete? false}}, :article-aliases {:display-defaults {:show-edit? true, :show-add-button? false, :per-page 10, :show-filtering? true}, :display-locks {}, :column-defaults {}, :column-locks {:id false}, :list-config {:form-display :modal}}, :store-aliases {:display-defaults {:per-page 25, :show-add-button? false, :show-delete? false, :show-edit? true, :show-filtering? true}, :display-locks {:show-add-button? false}, :column-defaults {}, :column-locks {:id false}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:edit :expenses/store-aliases.manage, :delete :expenses/store-aliases.manage}}}, :expense-categories {:display-locks {}, :display-defaults {:show-add-button? true, :show-delete? true, :show-edit? true, :show-filtering? true, :show-highlights? true, :show-pagination? true, :per-page 50}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/expense-categories.manage, :edit :expenses/expense-categories.manage, :delete :expenses/expense-categories.manage}}}, :backlog {:display-defaults {}, :display-locks {}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal}}, :tenants {:display-defaults {:show-add-button? false, :show-filtering? false, :per-page 25}, :display-locks {}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal}}, :users {:display-defaults {:show-add-button? true, :show-edit? true, :per-page 25}, :display-locks {:show-batch-edit? true}, :column-defaults {}, :column-locks {:full-name true}, :list-config {:form-display :modal}}}	2026-09-24 18:58:01.197971+00	2026-09-24 18:58:01.197971+00
f5d165a3-6a9e-44e4-9200-5bad8e911ce9	admin	form-fields	{:expense-items {:edit-fields ["article_id" "qty" "unit" "unit_price" "line_total"], :field-config {:article_id {:type "select", :label "Article", :options ["articles" "canonical_name"]}, :qty {:type "number", :step 0.001}, :unit {:type "select", :label "Unit", :options ["kom" "kg" "g" "l" "pak"]}, :unit_price {:type "number", :step 0.01}, :line_total {:type "number", :step 0.01}}, :removed-fields {:edit-fields ["raw_label"]}}, :subcategories {:create-fields ["category_id" "name" "description"], :edit-fields ["category_id" "name" "description"], :field-config {:category_id {:type "select", :label "Category", :options ["categories" "name"]}, :name {:type "text", :label "Name"}, :description {:type "textarea", :label "Description"}}}, :cities {:create-fields ["name"], :edit-fields ["name"], :field-config {:name {:type "text", :label "Name"}}}, :admins {:create-fields [:email :full-name :role :status], :edit-fields [:email :full-name :role :status], :required-fields [:email], :field-config {:email {:type :email, :validation :email, :placeholder "admin@example.com"}, :full-name {:type :text, :max-length 255, :placeholder "Full Name"}, :role {:type :select, :options [:admin :support :owner], :default :admin}, :status {:type :select, :options [:active :suspended], :default :active}}}, :articles {:edit-fields ["manufacturer_id" "canonical_name" "unit" "subcategory_id" "link" "normalized_key"], :field-config {:unit {:type "select", :label "Unit", :default "kom", :options ["kom" "kg" "g" "l" "pak"]}, :manufacturer_id {:type "select", :label "Manufacturer", :options ["manufacturers" "display_name"]}, :subcategory_id {:type "select", :label "Subcategory", :options ["subcategories" "name"]}}}, :expenses {:create-fields ["purchased_at" "supplier_id" "currency" "total_amount" "notes"], :edit-fields ["purchased_at" "supplier_id" "currency" "total_amount" "notes"], :batch-edit-fields ["purchased_at" "supplier_id" "currency" "total_amount" "notes"], :field-config {:purchased_at {:type "datetime-local"}, :supplier_id {:type "select", :label "Supplier", :options ["suppliers" "display_name"]}, :payer_id {:type "select", :label "Payer", :options ["payers" "label"]}, :expense_category_id {:type "select", :label "Expense Category", :options ["expense-categories" "name"]}, :currency {:type "select", :label "Currency", :options ["BAM" "EUR" "USD"]}, :total_amount {:type "number", :step 0.01}, :notes {:type "textarea"}}}, :manufacturers {:create-fields ["display_name"], :edit-fields ["display_name"], :field-config {:display_name {:type "text", :label "Display name"}}}, :categories {:create-fields ["name" "description"], :edit-fields ["name" "description"], :field-config {:name {:type "text", :label "Name"}, :description {:type "textarea", :label "Description"}}}, :supplier-aliases {:edit-fields ["raw_label" "raw_label_normalized" "supplier_id"], :field-config {:supplier_id {:type "select", :label "Supplier", :options ["suppliers" "display_name"]}}}, :stores {:edit-fields ["display_name" "address"], :field-config {:display_name {:type "text", :label "Display name"}, :address {:type "textarea", :label "Address"}}}, :article-aliases {:edit-fields ["raw_label" "raw_label_normalized" "unit" "supplier_id" "article_id" "confidence"], :field-config {:supplier_id {:type "select", :label "Supplier", :options ["suppliers" "display_name"]}, :unit {:type "select", :label "Unit", :options ["kom" "kg" "g" "l" "pak"]}, :article_id {:type "select", :label "Article", :options ["articles" "canonical_name"]}}}, :store-aliases {:edit-fields ["raw_label" "raw_label_normalized" "store_id" "confidence"], :field-config {:raw_label {:type "text", :label "Raw label"}, :raw_label_normalized {:type "text", :label "Alias"}, :store_id {:type "select", :label "Store", :options ["stores" "display_name"]}, :confidence {:type "number", :step 1}}}, :expense-categories {:create-fields ["name" "exclude_from_reports" "is_default"], :edit-fields ["name" "exclude_from_reports" "is_default"], :field-config {:name {:type "text", :label "Name"}, :exclude_from_reports {:type "checkbox", :label "Exclude from reports"}, :is_default {:type "checkbox", :label "Default expense category"}}}, :backlog {:create-fields ["description" "status" "type"], :edit-fields ["description" "status" "type"], :required-fields ["description" "type"], :field-config {:number {:type "number", :label "Number", :step 1}, :description {:type "textarea", :label "Description"}, :status {:type "select", :label "Status", :default "Waiting", :options [{:value "Waiting", :label "Waiting", :color "#64748b"} {:value "In progress", :label "In Progress", :color "#0ea5e9"} {:value "Completed", :label "Completed", :color "#10b981"} {:value "Need improvements", :label "Need Improvements", :color "#f59e0b"}]}, :type {:type "select", :label "Type", :options [{:value "Issue", :label "Issue", :color "#ef4444"} {:value "Feature", :label "Feature", :color "#22c55e"} {:value "Refactoring", :label "Refactoring", :color "#f59e0b"} {:value "Review", :label "Review", :color "#0ea5e9"} {:value "Improvement", :label "Improvement", :color "#6366f1"}]}}}, :users {:create-fields ["email" "status" "full-name"], :edit-fields ["email" "full-name" "status"], :required-fields ["email"], :field-config {:email {:type "email", :validation "email"}, :full-name {:type "text", :max-length 255, :placeholder "Full Name"}, :status {:type "select", :options ["active" "inactive" "suspended"]}}}}	2026-09-24 18:58:01.213462+00	2026-09-24 18:58:01.213462+00
68ed299e-fd27-44a6-b878-59a43f166e94	admin	navigation	{:title "Admin Panel", :sections [{:id :system-administration, :title "System Administration", :items [{:id :dashboard, :label "Dashboard"} {:id :backlog, :label "Backlog"} {:id :tenants, :label "Tenants & Memberships"} {:id :users, :label "User Accounts"} {:id :admins, :label "Admins"} {:id :audit-logs, :label "Audit Logs"} {:id :login-events, :label "Login Events"}]} {:id :expenses, :title "Expenses", :items [{:id :reports, :label "Reports"} {:id :expenses-settings, :label "Settings"} {:id :search, :label "Search"} {:id :expenses, :label "Expenses"} {:id :receipts, :label "Receipts"} {:id :articles, :label "Articles"} {:id :manufacturers, :label "Manufacturers"} {:id :categories, :label "Categories"} {:id :subcategories, :label "Subcategories"} {:id :suppliers, :label "Suppliers"} {:id :stores, :label "Stores"} {:id :countries, :label "Countries"} {:id :cities, :label "Cities"} {:id :unmapped-aliases, :label "Unmapped Aliases"} {:id :article-aliases, :label "Article Aliases"} {:id :supplier-aliases, :label "Supplier Aliases"} {:id :store-aliases, :label "Store Aliases"} {:id :duplicates, :label "Dedup & Merge"}]}]}	2026-09-24 18:58:01.23586+00	2026-09-24 18:58:01.23586+00
2d9dc755-61fd-4452-bc34-768eabd6f81a	user	entities	{:expense-contexts {:title "Expense Contexts"}, :expense-items {:title "Expense Items"}, :suppliers {:title "Suppliers"}, :subcategories {:title "Subcategories"}, :cities {:title "Cities"}, :articles {:title "Articles"}, :expenses {:title "My Expenses"}, :manufacturers {:title "Manufacturers"}, :tenant-members {:title "Tenant Members"}, :payers {:title "Payers"}, :categories {:title "Categories"}, :supplier-aliases {:title "Supplier Aliases"}, :receipts {:title "Receipts"}, :stores {:title "Stores"}, :article-aliases {:title "Article Aliases"}, :store-aliases {:title "Store Aliases"}, :expense-categories {:title "Expense Categories"}}	2026-09-24 18:58:01.241799+00	2026-09-24 18:58:01.241799+00
b4e474fa-959a-4371-9acf-a5615e90e07b	user	view-options	{:expense-contexts {:display-locks {}, :display-defaults {:show-add-button? true, :show-delete? true, :show-edit? true, :show-filtering? true, :show-highlights? true, :show-pagination? true, :per-page 50}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/expense-contexts.manage, :edit :expenses/expense-contexts.manage, :delete :expenses/expense-contexts.manage}}}, :expense-items {:display-defaults {:show-add-button? false, :per-page 25}, :display-locks {}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal}}, :suppliers {:display-locks {:show-batch-edit? false}, :display-defaults {:per-page 100, :show-edit? false}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/reference.write, :edit :expenses/reference.write, :delete :expenses/reference.write}}}, :subcategories {:display-locks {}, :display-defaults {:show-add-button? false, :show-delete? false, :show-edit? false, :show-filtering? true, :show-highlights? true, :show-pagination? true, :per-page 50}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/subcategories.manage, :edit :expenses/subcategories.manage, :delete :expenses/subcategories.manage}}}, :cities {:display-locks {}, :display-defaults {:show-add-button? true, :show-delete? true, :show-edit? true, :show-filtering? true, :show-highlights? true, :show-pagination? true, :per-page 50}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/cities.manage, :edit :expenses/cities.manage, :delete :expenses/cities.manage}}}, :articles {:display-locks {}, :display-defaults {:show-edit? false, :show-delete? false, :per-page 25}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/articles.manage, :edit :expenses/articles.manage, :delete :expenses/articles.manage}}}, :expenses {:display-defaults {:show-add-button? false, :show-highlights? true, :show-pagination? true, :per-page 25}, :display-locks {:show-batch-delete? true, :show-batch-edit? true}, :column-locks {:receipt_id false, :created_at true}, :column-defaults {:purchased_at false}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/can-write, :edit :expenses/can-write, :delete :expenses/can-write}}}, :manufacturers {:display-defaults {:per-page 25}, :display-locks {}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/manufacturers.manage, :edit :expenses/manufacturers.manage, :delete :expenses/manufacturers.manage}}}, :tenant-members {:display-defaults {:show-add-button? false, :show-filtering? false, :show-select? false, :show-batch-edit? false, :show-batch-delete? false, :per-page 25}, :display-locks {}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal}}, :unmapped-aliases {:display-defaults {:show-add-button? false, :show-edit? false, :show-delete? false, :show-highlights? false, :show-batch-edit? false, :show-batch-delete? false}, :display-locks {:show-add-button? false, :show-edit? false, :show-delete? false, :show-highlights? false, :show-batch-edit? false, :show-batch-delete? false}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal}}, :payers {:display-locks {}, :display-defaults {:per-page 50, :show-edit? false}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/reference.write, :edit :expenses/reference.write, :delete :expenses/reference.write}}}, :categories {:display-locks {}, :display-defaults {:show-add-button? true, :show-delete? true, :show-edit? true, :show-filtering? true, :show-highlights? true, :show-pagination? true, :per-page 50}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/categories.manage, :edit :expenses/categories.manage, :delete :expenses/categories.manage}}}, :supplier-aliases {:display-defaults {:show-edit? false, :show-add-button? false, :per-page 25}, :display-locks {}, :column-defaults {}, :column-locks {:id false}, :list-config {:form-display :modal}}, :receipts {:display-defaults {:show-select? true, :show-pagination? true, :show-add-button? true, :show-filtering? true, :show-highlights? true, :per-page 50}, :display-locks {:show-batch-edit? false, :show-batch-delete? true}, :column-defaults {:created-at true, :updated-at true}, :column-locks {}, :list-config {:action-gates {:add :expenses/can-write, :select :expenses/can-write}}}, :stores {:display-defaults {:show-edit? false, :show-delete? false, :per-page 10}, :display-locks {}, :column-defaults {}, :column-locks {:id false}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/stores.manage, :edit :expenses/stores.manage, :delete :expenses/stores.manage}}}, :article-aliases {:display-defaults {:show-edit? false, :show-add-button? false, :per-page 25}, :display-locks {}, :column-defaults {}, :column-locks {:id false}, :list-config {:form-display :modal}}, :store-aliases {:display-defaults {:per-page 100, :show-add-button? false, :show-delete? false, :show-edit? true}, :display-locks {:show-add-button? false}, :column-defaults {}, :column-locks {:id false}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:edit :expenses/store-aliases.manage, :delete :expenses/store-aliases.manage}}}, :expense-categories {:display-locks {}, :display-defaults {:show-add-button? true, :show-delete? true, :show-edit? true, :show-filtering? true, :show-highlights? true, :show-pagination? true, :per-page 50}, :column-defaults {}, :column-locks {}, :list-config {:form-display :modal, :disallowed-action-mode :disable, :action-gates {:add :expenses/expense-categories.manage, :edit :expenses/expense-categories.manage, :delete :expenses/expense-categories.manage}}}}	2026-09-24 18:58:01.248276+00	2026-09-24 18:58:01.248276+00
00ab78ce-3335-4de8-b948-33cf5527be7d	user	form-fields	{:expense-items {:edit-fields ["article_id" "qty" "unit" "unit_price" "line_total"], :field-config {:article_id {:type "select", :label "Article", :options ["articles" "canonical_name"]}, :qty {:type "number", :step 0.001}, :unit {:type "select", :label "Unit", :options ["kom" "kg" "g" "l" "pak"]}, :unit_price {:type "number", :step 0.01}, :line_total {:type "number", :step 0.01}}, :removed-fields {:edit-fields ["raw_label"]}}, :subcategories {:create-fields ["category_id" "name" "description"], :edit-fields ["name" "description"], :field-config {:category_id {:type "select", :label "Category", :options ["categories" "name"]}, :name {:type "text", :label "Name"}, :description {:type "textarea", :label "Description"}}}, :cities {:create-fields ["name"], :edit-fields ["name"], :field-config {:name {:type "text", :label "Name"}}}, :articles {:edit-fields ["manufacturer_id" "canonical_name" "unit" "subcategory_id" "link" "normalized_key"], :field-config {:unit {:type "select", :label "Unit", :default "kom", :options ["kom" "kg" "g" "l" "pak"]}, :manufacturer_id {:type "select", :label "Manufacturer", :options ["manufacturers" "display_name"]}, :subcategory_id {:type "select", :label "Subcategory", :options ["subcategories" "name"]}}}, :expenses {:create-fields ["purchased_at" "supplier_id" "payer_id" "expense_category_id" "currency" "total_amount" "notes"], :edit-fields ["purchased_at" "supplier_id" "payer_id" "expense_category_id" "currency" "total_amount" "notes"], :batch-edit-fields ["purchased_at" "supplier_id" "payer_id" "expense_category_id" "currency" "total_amount" "notes"], :field-config {:purchased_at {:type "datetime-local"}, :supplier_id {:type "select", :label "Supplier", :options ["suppliers" "display_name"]}, :payer_id {:type "select", :label "Payer", :options ["payers" "label"]}, :expense_category_id {:type "select", :label "Expense Category", :options ["expense-categories" "name"]}, :currency {:type "select", :label "Currency", :options ["BAM" "EUR" "USD"]}, :total_amount {:type "number", :step 0.01}, :notes {:type "textarea"}}}, :manufacturers {:create-fields ["display_name"], :edit-fields ["display_name"], :field-config {:display_name {:type "text", :label "Display name"}}}, :categories {:create-fields ["name" "description"], :edit-fields ["name" "description"], :field-config {:name {:type "text", :label "Name"}, :description {:type "textarea", :label "Description"}}}, :supplier-aliases {:edit-fields ["raw_label" "raw_label_normalized" "supplier_id"], :field-config {:supplier_id {:type "select", :label "Supplier", :options ["suppliers" "display_name"]}}}, :stores {:edit-fields ["display_name" "address"], :field-config {:display_name {:type "text", :label "Display name"}, :address {:type "textarea", :label "Address"}}}, :article-aliases {:edit-fields ["raw_label" "raw_label_normalized" "unit" "supplier_id" "article_id"], :field-config {:supplier_id {:type "select", :label "Supplier", :options ["suppliers" "display_name"]}, :unit {:type "select", :label "Unit", :options ["kom" "kg" "g" "l" "pak"]}, :article_id {:type "select", :label "Article", :options ["articles" "canonical_name"]}}}, :store-aliases {:edit-fields ["raw_label" "raw_label_normalized" "store_id" "confidence"], :field-config {:raw_label {:type "text", :label "Raw label"}, :raw_label_normalized {:type "text", :label "Alias"}, :store_id {:type "select", :label "Store", :options ["stores" "display_name"]}, :confidence {:type "number", :step 1}}}, :expense-categories {:create-fields ["name" "exclude_from_reports" "is_default"], :edit-fields ["name" "exclude_from_reports" "is_default"], :field-config {:name {:type "text", :label "Name"}, :exclude_from_reports {:type "checkbox", :label "Exclude from reports"}, :is_default {:type "checkbox", :label "Default expense category"}}}}	2026-09-24 18:58:01.258465+00	2026-09-24 18:58:01.258465+00
ea5a8405-6a1e-4f7c-a43b-0b3b58167ce5	user	navigation	{:title "Troškovi", :sections [{:id :expenses, :title "Troškovi", :items [{:id :search, :label "Pretraga"} {:id :dashboard, :label "Nadzorna ploča"} {:id :expenses-list, :label "Troškovi"} {:id :receipts, :label "Računi"} {:id :expense-items, :label "Stavke troškova"}]} {:id :operations, :title "Operacije", :items [{:id :upload, :label "Učitaj"} {:id :reports, :label "Izvještaji"} {:id :unmapped-items, :label "Nemapirani aliasi"}]} {:id :reference, :title "Reference", :items [{:id :suppliers, :label "Dobavljači"} {:id :payers, :label "Kupci"} {:id :expense-categories, :label "Kategorije troškova"} {:id :stores, :label "Trgovine"}]} {:id :workspace, :title "Radni prostor", :items [{:id :onboarding, :label "Postavljanje"} {:id :management-guide, :label "Vodič"} {:id :members, :label "Članovi"}]}]}	2026-09-24 18:58:01.275977+00	2026-09-24 18:58:01.275977+00
fbeaae7e-df26-43a7-ae3c-20b6da120a88	admin	table-columns	{:expense-contexts {:available-columns ["name" "description" "is_active" "created_at" "updated_at" "id" "tenant_id"], :default-visible-columns ["name" "description" "is_active" "created_at"], :filterable-columns ["name" "description" "is_active" "created_at"], :sortable-columns ["name" "is_active" "created_at" "updated_at"], :always-visible ["name"], :column-metadata {:name {:label-key :common/name}, :description {:label-key :common/description}, :is_active {:label-key :common/active}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}}, :expense-items {:available-columns ["expense_purchased_at" "article_canonical_name" "raw_label" "alias_id" "qty" "unit" "unit_price" "line_total" "article_id" "created_at" "expense_id" "id" "tenant_id" "updated_at"], :default-visible-columns ["expense_purchased_at" "article_canonical_name" "raw_label" "qty" "unit" "unit_price" "line_total" "created_at"], :filterable-columns ["expense_purchased_at" "article_canonical_name" "raw_label" "alias_id" "qty" "unit" "unit_price" "line_total" "created_at" "expense_id"], :sortable-columns ["expense_purchased_at" "article_canonical_name" "raw_label" "qty" "unit" "unit_price" "line_total" "created_at"], :always-visible ["raw_label"], :computed-fields {:raw_label {}, :expense_purchased_at {:type "timestamp"}, :article_canonical_name {}}, :column-config {:alias_id {:type "text"}, :article_id {:type "text"}, :expense_id {:type "text"}}, :column-metadata {:unit_price {:label-key :common/unit-price}, :unit {:label-key :common/unit}, :raw_label {:label-key :common/raw-label}, :line_total {:label-key :common/line-total}, :article_id {:label-key :common/article-id}, :expense_purchased_at {:label-key :common/purchased-at}, :alias_id {:label-key :common/alias-id}, :article_canonical_name {:label-key :common/article}, :qty {:label-key :common/qty}, :expense_id {:label-key :common/expense-id}, :created_at {:label-key :common/created-at}}}, :suppliers {:available-columns ["display_name" "normalized_key" "store_count" "created_at" "id" "updated_at"], :default-visible-columns ["display_name" "normalized_key" "store_count" "created_at"], :filterable-columns ["display_name" "normalized_key" "created_at"], :sortable-columns ["display_name" "normalized_key" "store_count" "created_at"], :always-visible ["display_name"], :computed-fields {:store_count {:label-key :common/stores, :type :number, :admin {:visible-in-table? true, :filterable? false, :sortable? true}}}, :column-metadata {:display_name {:label-key :common/supplier-name}, :normalized_key {:label-key :common/normalized-key}, :store_count {:label-key :common/stores}, :created_at {:label-key :common/created-at}, :id {:label-key :common/id}, :updated_at {:label-key :common/updated-at}}}, :subcategories {:available-columns ["category_name" "name" "description" "created_at" "category_id" "updated_at" "id"], :default-visible-columns ["name" "category_name" "description" "created_at"], :filterable-columns ["name" "category_name" "description" "created_at"], :sortable-columns ["name" "category_name" "created_at" "updated_at"], :always-visible ["name"], :computed-fields {:category_name {:type "string", :label "Category"}}, :column-config {:category_id {:type "text"}, :is_active {:type "boolean"}, :is_system_default {:type "boolean"}}, :column-metadata {:description {:label-key :common/description}, :name {:label-key :common/subcategory-name}, :is_active {:label "Active"}, :updated_at {:label-key :common/updated-at}, :category_name {:label-key :common/category}, :id {:label-key :common/id}, :category_id {:label-key :common/category-id}, :created_at {:label-key :common/created-at}, :is_system_default {:label "System default"}}}, :cities {:available-columns ["name" "normalized_key" "zip" "country" "created_at" "updated_at" "id"], :default-visible-columns ["name" "country" "zip" "normalized_key" "created_at"], :filterable-columns ["name" "normalized_key" "zip" "country" "created_at"], :sortable-columns ["name" "normalized_key" "zip" "country" "created_at" "updated_at"], :always-visible ["name"], :column-metadata {:name {:label-key :common/city-name}, :normalized_key {:label-key :common/normalized-key}, :zip {:label-key :common/zip}, :country {:label-key :common/country}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}, :computed-fields {}, :column-config {}}, :admins {:available-columns ["id" "email" "full-name" "role" "status" "last-login-at" "created-at" "updated-at"], :default-visible-columns ["id" "email" "full-name" "role" "status" "last-login-at" "created-at" "updated-at"], :filterable-columns ["email" "full-name" "role" "status" "last-login-at"], :sortable-columns ["full-name" "role" "status" "created-at"], :always-visible [], :computed-fields {}, :column-config {:email {:width "220px"}, :role {:width "100px", :formatter "admin-role-badge"}, :updated-at {:width "140px", :formatter "date"}, :last-login-at {:width "160px", :formatter "date"}, :status {:width "100px", :formatter "status-badge"}, :full-name {:width "180px"}, :created-at {:width "140px", :formatter "date"}}, :column-metadata {}}, :articles {:available-columns ["canonical_name" "unit" "category_name" "subcategory_id" "subcategory_name" "manufacturer_display_name" "manufacturer_id" "link" "normalized_key" "created_at" "updated_at" "id"], :default-visible-columns ["canonical_name" "unit" "category_name" "subcategory_name" "manufacturer_display_name" "created_at"], :filterable-columns ["canonical_name" "unit" "category_name" "subcategory_name" "manufacturer_display_name" "link" "normalized_key" "created_at" "updated_at"], :sortable-columns ["canonical_name" "unit" "category_name" "subcategory_name" "manufacturer_display_name" "link" "normalized_key" "created_at" "updated_at"], :always-visible ["canonical_name"], :computed-fields {:manufacturer_display_name {:type "string", :label "Manufacturer"}, :category_name {:type "string", :label "Category"}, :subcategory_name {:type "string", :label "Subcategory"}}, :column-config {:subcategory_id {:type "text"}, :manufacturer_id {:type "text"}}, :column-metadata {:unit {:label-key :common/unit}, :manufacturer_display_name {:label-key :common/manufacturer}, :manufacturer_id {:label-key :common/manufacturer-id}, :normalized_key {:label-key :common/normalized-key}, :updated_at {:label-key :common/updated-at}, :category_name {:label-key :common/category}, :link {:label-key :common/link}, :id {:label-key :common/id}, :subcategory_name {:label-key :common/subcategory}, :canonical_name {:label-key :common/article}, :subcategory_id {:label-key :common/subcategory-id}, :created_at {:label-key :common/created-at}}}, :expenses {:available-columns ["purchased_at" "supplier_display_name" "payer_label" "currency" "total_amount" "item_count" "notes" "id" "receipt_id" "created_at" "updated_at" "supplier_id" "payer_id" "store_id" "tenant_id" "expense_category_id"], :computed-fields {:supplier_display_name {:type "string", :label "Supplier"}, :store_display_name {:type "string", :label "Store"}, :expense_category_name {:type "string", :label "Expense Category"}, :expense_context_name {:type "string", :label "Expense Context"}, :item_count {:label-key :common/items, :type :number, :admin {:visible-in-table? true, :filterable? false, :sortable? true}}, :payer_label {:type "string", :label "Payer"}}, :default-visible-columns ["purchased_at" "supplier_display_name" "payer_label" "currency" "total_amount" "item_count"], :filterable-columns ["purchased_at" "supplier_display_name" "payer_label" "currency" "total_amount" "created_at" "updated_at"], :sortable-columns ["purchased_at" "supplier_display_name" "payer_label" "currency" "total_amount" "item_count" "created_at" "updated_at"], :always-visible [], :column-config {:receipt_id {:type "text"}, :supplier_id {:type "text"}, :payer_id {:type "text"}, :store_id {:type "text"}, :expense_category_id {:type "text"}, :expense_context_id {:type "text"}}, :column-metadata {:expense_category_name {:label-key :common/expense-category}, :payer_label {:label-key :common/payer}, :receipt_id {:label-key :common/receipt-id}, :item_count {:label-key :common/items}, :supplier_display_name {:label-key :common/supplier}, :payer_id {:label-key :common/payer-id}, :updated_at {:label-key :common/updated-at}, :currency {:label-key :common/currency}, :expense_category_id {:label-key :common/expense-category-id}, :store_display_name {:label-key :common/store}, :supplier_id {:label-key :common/supplier-id}, :id {:label-key :common/id}, :expense_context_name {:label-key :common/expense-context}, :notes {:label-key :common/notes}, :purchased_at {:label-key :common/purchased-at}, :total_amount {:label-key :common/total}, :store_id {:label-key :common/store-id}, :created_at {:label-key :common/created-at}, :expense_context_id {:label-key :common/expense-context-id}}}, :manufacturers {:available-columns ["display_name" "normalized_key" "created_at" "updated_at" "id"], :default-visible-columns ["display_name" "normalized_key" "created_at"], :filterable-columns ["display_name" "normalized_key" "created_at"], :sortable-columns ["display_name" "normalized_key" "created_at"], :always-visible ["display_name"], :column-metadata {:display_name {:label-key :common/manufacturer-name}, :normalized_key {:label-key :common/normalized-key}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}}, :unmapped-aliases {:available-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "unit" "occurrence_count" "supplier_id" "id"], :default-visible-columns ["supplier_display_name" "raw_label" "unit" "occurrence_count"], :filterable-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "unit" "occurrence_count"], :sortable-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "unit" "occurrence_count"], :always-visible ["raw_label"], :computed-fields {:supplier_display_name {}}, :column-config {:supplier_id {:type "text"}, :supplier_display_name {:width "220px"}, :raw_label {:width "260px"}, :raw_label_normalized {:width "220px"}, :unit {:width "90px"}, :occurrence_count {:width "110px"}}, :column-metadata {:supplier_display_name {:label-key :common/supplier}, :raw_label {:label-key :common/raw-label}, :raw_label_normalized {:label-key :common/normalized}, :unit {:label-key :common/unit}, :occurrence_count {:label-key :common/occurrences}, :supplier_id {:label-key :common/supplier-id}, :id {:label-key :common/id}}}, :countries {:available-columns ["country" "code" "created_at" "updated_at" "id"], :default-visible-columns ["country" "code"], :filterable-columns ["country" "code"], :sortable-columns ["country" "code" "created_at" "updated_at"], :always-visible ["country"], :computed-fields {:id {:type "string"}}, :column-metadata {:country {:label-key :common/country}, :code {:label-key :common/code}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}, :column-config {}}, :payers {:available-columns ["label" "is_default" "created_at" "updated_at" "id" "tenant_id"], :default-visible-columns ["label" "is_default"], :filterable-columns ["label" "is_default"], :sortable-columns ["label" "is_default"], :always-visible ["label"], :computed-fields {:user_email {:type "string", :label "Email"}, :user_full_name {:type "string", :label "User"}}, :column-metadata {:label {:label-key :common/label}, :is_default {:label-key :common/is-default}, :user_full_name {:label "User"}, :user_email {:label "Email"}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}}, :categories {:available-columns ["name" "description" "created_at" "updated_at" "id"], :default-visible-columns ["name" "description" "created_at"], :filterable-columns ["name" "description" "created_at"], :sortable-columns ["name" "created_at" "updated_at"], :always-visible ["name"], :column-metadata {:name {:label-key :common/category-name}, :description {:label-key :common/description}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}, :computed-fields {}, :column-config {}}, :supplier-aliases {:available-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "confidence" "created_at" "updated_at" "supplier_id" "id"], :default-visible-columns ["supplier_display_name" "raw_label" "raw_label_normalized"], :filterable-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "confidence" "created_at"], :sortable-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "confidence" "created_at"], :always-visible ["raw_label"], :computed-fields {:supplier_display_name {}}, :column-config {:supplier_id {:type "text"}}, :column-metadata {:supplier_display_name {:label-key :common/supplier}, :raw_label {:label-key :common/raw-label}, :raw_label_normalized {:label-key :common/normalized}, :confidence {:label-key :common/confidence}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :supplier_id {:label-key :common/supplier-id}, :id {:label-key :common/id}}}, :audit-logs {:available-columns ["created-at" "action" "actor-display-name" "entity-name" "context-summary" "target-type" "operation" "severity" "http-status" "error-message" "admin-ref" "admin-name" "user-agent" "id" "actor-type" "actor-id" "target-id" "metadata" "ip" "updated-at"], :default-visible-columns ["created-at" "action" "actor-display-name" "entity-name" "context-summary"], :filterable-columns ["action" "entity-name" "admin-ref" "admin-name" "user-agent"], :sortable-columns ["action" "entity-name" "admin-ref" "admin-name" "user-agent"], :always-visible ["action"], :computed-fields {:actor-display-name {:type "adapter-derived", :dependencies ["actor-type" "actor-id" "admin-ref" "admin-name"]}, :entity-name {:type "join", :compute-fn "join-entity-name", :dependencies ["target-type" "target-id" "metadata"]}, :context-summary {:type "adapter-derived", :dependencies ["metadata" "target-type"]}, :operation {:type "metadata", :dependencies ["metadata"]}, :severity {:type "metadata", :dependencies ["metadata"]}, :http-status {:type "metadata", :dependencies ["metadata"]}, :error-message {:type "metadata", :dependencies ["metadata"]}}, :column-config {:user-agent {:width "200px"}, :context-summary {:width "360px", :type "text"}, :operation {:width "160px", :type "text"}, :actor-display-name {:width "160px", :type "text"}, :admin-ref {:width "180px"}, :http-status {:width "110px"}, :severity {:width "110px", :formatter "status-badge"}, :entity-name {:width "200px", :computed-field true, :type "text"}, :action {:width "140px"}, :error-message {:width "320px", :type "text"}, :admin-name {:width "140px"}}, :column-metadata {:actor-display-name {:label "Actor"}, :entity-name {:label "Subject"}, :context-summary {:label "Details"}, :target-type {:label "Target type"}, :operation {:label "Operation"}, :severity {:label "Severity"}, :http-status {:label "HTTP status"}, :error-message {:label "Error message"}}}, :receipts {:available-columns ["status" "supplier-guess" "created-at" "updated-at" "id" "file-size" "raw-extract-json" "parsed-markdown" "total-amount-guess" "currency-guess" "purchased-at-guess" "error-message" "error-details" "retry-count" "expense-id" "payer-id" "storage-key" "file-hash" "content-type" "supplier-alias-id" "store-guess" "store-alias-id" "tenant-id"], :default-visible-columns ["status" "supplier-guess" "created-at" "updated-at"], :filterable-columns ["status" "supplier-guess" "created-at" "updated-at"], :sortable-columns ["status" "supplier-guess" "created-at" "updated-at"], :always-visible ["status" "purchased-at-guess"], :computed-fields {}, :column-config {:expense-id {:type "text"}, :payer-id {:type "text"}, :supplier-alias-id {:type "text"}, :store-alias-id {:type "text"}}, :column-metadata {:raw-extract-json {:label-key :common/raw-extract-json}, :updated-at {:label-key :common/updated-at}, :total-amount-guess {:label-key :common/total-amount-guess}, :purchased-at-guess {:label-key :common/purchased-at-guess}, :file-size {:label-key :common/file-size}, :store-alias-id {:label-key :common/store-alias}, :currency-guess {:label-key :common/currency-guess}, :store-guess {:label-key :common/store-guess}, :expense-id {:label-key :common/expense-id}, :status {:label-key :common/status}, :file-hash {:label-key :common/file-hash}, :payer-id {:label-key :common/payer-id}, :id {:label-key :common/id}, :content-type {:label-key :common/content-type}, :supplier-alias-id {:label-key :common/supplier-alias}, :error-details {:label-key :common/error-details}, :storage-key {:label-key :common/storage-key}, :error-message {:label-key :common/error-message}, :supplier-guess {:label-key :common/supplier-guess}, :retry-count {:label-key :common/retry-count}, :created-at {:label-key :common/created-at}, :parsed-markdown {:label-key :common/parsed-markdown}}}, :stores {:available-columns ["supplier_display_name" "display_name" "normalized_key" "address" "city_name" "created_at" "supplier_id" "city_id" "updated_at" "id"], :default-visible-columns ["display_name" "supplier_display_name" "normalized_key" "address" "city_name" "created_at"], :filterable-columns ["display_name" "supplier_display_name" "normalized_key" "address" "city_name" "created_at"], :sortable-columns ["display_name" "supplier_display_name" "normalized_key" "address" "city_name" "created_at" "updated_at"], :always-visible ["display_name"], :computed-fields {:supplier_display_name {:type "string", :label "Supplier"}, :city_name {:type "string", :label "City"}}, :column-config {:supplier_id {:type "text"}, :city_id {:type "text"}}, :column-metadata {:address {:label-key :common/address}, :supplier_display_name {:label-key :common/supplier}, :normalized_key {:label-key :common/normalized-key}, :city_name {:label-key :common/city}, :updated_at {:label-key :common/updated-at}, :supplier_id {:label-key :common/supplier-id}, :id {:label-key :common/id}, :city_id {:label-key :common/city-id}, :display_name {:label-key :common/store-name}, :created_at {:label-key :common/created-at}}}, :login-events {:available-columns ["created-at" "principal-type" "success" "reason" "user-agent" "id" "principal-id" "ip" "updated-at"], :default-visible-columns ["created-at" "principal-type" "success" "reason"], :filterable-columns ["created-at" "principal-type" "success" "reason" "user-agent"], :sortable-columns ["created-at" "principal-type" "success" "user-agent"], :always-visible ["user-agent"], :computed-fields {}, :column-config {:created-at {:width "180px", :formatter "date"}, :principal-type {:width "110px"}, :success {:width "100px"}, :reason {:width "200px"}, :user-agent {:width "260px"}}}, :article-aliases {:available-columns ["supplier_display_name" "article_canonical_name" "raw_label" "raw_label_normalized" "unit" "created_at" "supplier_id" "article_id" "id" "updated_at"], :default-visible-columns ["supplier_display_name" "article_canonical_name" "raw_label" "raw_label_normalized" "unit" "created_at"], :filterable-columns ["supplier_display_name" "article_canonical_name" "raw_label" "raw_label_normalized" "unit" "created_at"], :sortable-columns ["supplier_display_name" "article_canonical_name" "raw_label" "raw_label_normalized" "unit" "created_at"], :always-visible ["raw_label"], :computed-fields {:supplier_display_name {}, :article_canonical_name {}}, :column-config {:supplier_id {:type "text"}, :article_id {:type "text"}, :supplier_display_name {:width "220px"}, :article_canonical_name {:width "260px"}, :raw_label {:width "260px"}, :raw_label_normalized {:width "220px"}, :unit {:width "90px"}, :created_at {:width "160px", :formatter "date"}}, :column-metadata {:unit {:label-key :common/unit}, :raw_label {:label-key :common/raw-label}, :raw_label_normalized {:label-key :common/normalized}, :supplier_display_name {:label-key :common/supplier}, :article_id {:label-key :common/article-id}, :supplier_id {:label-key :common/supplier-id}, :id {:label-key :common/id}, :article_canonical_name {:label-key :common/article}, :created_at {:label-key :common/created-at}}}, :store-aliases {:available-columns ["supplier_display_name" "store_display_name" "store_address" "raw_label" "raw_label_normalized" "confidence" "created_at" "updated_at" "store_id" "id"], :default-visible-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "store_display_name" "confidence" "created_at"], :filterable-columns ["supplier_display_name" "store_display_name" "store_address" "raw_label" "raw_label_normalized" "confidence" "created_at"], :sortable-columns ["supplier_display_name" "store_display_name" "raw_label" "raw_label_normalized" "confidence" "created_at"], :always-visible ["raw_label"], :computed-fields {:supplier_display_name {}, :store_display_name {}, :store_address {}}, :column-config {:store_id {:type "text"}}, :column-metadata {:store_address {:label-key :common/address}, :raw_label {:label-key :common/raw-label}, :raw_label_normalized {:label-key :common/normalized}, :supplier_display_name {:label-key :common/supplier}, :updated_at {:label-key :common/updated-at}, :store_display_name {:label-key :common/store}, :id {:label-key :common/id}, :confidence {:label-key :common/confidence}, :store_id {:label-key :common/store-id}, :created_at {:label-key :common/created-at}}}, :expense-categories {:available-columns ["name" "exclude_from_reports" "is_default" "created_at" "updated_at" "id" "tenant_id"], :default-visible-columns ["name" "is_default" "exclude_from_reports" "created_at"], :filterable-columns ["name" "is_default" "exclude_from_reports" "created_at"], :sortable-columns ["name" "is_default" "exclude_from_reports" "created_at" "updated_at"], :always-visible ["name"], :column-metadata {:name {:label-key :common/expense-category-name}, :exclude_from_reports {:label "Exclude from reports"}, :is_default {:label-key :common/is-default}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}, :computed-fields {}, :column-config {}}, :backlog {:available-columns ["number" "description" "status" "type" "created_at" "updated_at"], :default-visible-columns ["number" "description" "status" "type"], :filterable-columns ["number" "description" "status" "type"], :sortable-columns ["number" "status" "type"], :always-visible ["number"], :computed-fields {}, :column-config {:status {:width "120px", :formatter "backlog-status-badge"}, :type {:width "130px", :formatter "backlog-type-badge"}}}, :users {:available-columns ["id" "email" "full-name" "status" "last-login-at" "created-at" "updated-at" "email-verified" "auth-provider" "user-ref" "email-masked" "password-hash"], :default-visible-columns ["email" "full-name" "status" "last-login-at" "created-at" "updated-at" "email-verified" "auth-provider"], :filterable-columns ["status" "last-login-at" "created-at" "updated-at"], :sortable-columns ["full-name" "status" "created-at"], :always-visible ["email"], :computed-fields {}, :column-config {:updated-at {:width "140px", :formatter "date"}, :email {:width "220px"}, :user-ref {:width "160px"}, :email-masked {:width "200px"}, :last-login-at {:width "160px", :formatter "date"}, :status {:width "100px", :formatter "status-badge"}, :full-name {:width "180px"}, :created-at {:width "140px", :formatter "date"}}, :removed-fields {:available-columns ["preferred-name"]}}}	2026-09-24 18:58:01.230135+00	2026-09-26 07:40:27.88034+00
25751218-1fd3-4db3-b2da-388174329097	user	table-columns	{:expense-contexts {:available-columns ["name" "description" "is_active" "created_at" "updated_at" "id" "tenant_id"], :default-visible-columns ["name" "description" "is_active" "created_at"], :filterable-columns ["name" "description" "is_active" "created_at"], :sortable-columns ["name" "is_active" "created_at" "updated_at"], :always-visible ["name"], :column-metadata {:name {:label-key :common/name}, :description {:label-key :common/description}, :is_active {:label-key :common/active}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}, :computed-fields {}, :column-config {}}, :expense-items {:available-columns ["expense_purchased_at" "article_canonical_name" "raw_label" "qty" "unit" "unit_price" "line_total" "created_at" "id" "tenant_id" "expense_id" "alias_id" "article_id" "updated_at"], :default-visible-columns ["expense_purchased_at" "article_canonical_name" "raw_label" "qty" "unit" "unit_price" "line_total" "created_at"], :filterable-columns ["expense_purchased_at" "article_canonical_name" "raw_label" "qty" "unit" "unit_price" "line_total" "created_at"], :sortable-columns ["expense_purchased_at" "article_canonical_name" "raw_label" "qty" "unit" "unit_price" "line_total" "created_at"], :always-visible [], :computed-fields {:raw_label {}, :expense_purchased_at {:type "timestamp"}, :article_canonical_name {}}, :column-config {:alias_id {:type "text"}, :article_id {:type "text"}, :expense_id {:type "text"}}, :column-metadata {:unit_price {:label-key :common/unit-price}, :unit {:label-key :common/unit}, :raw_label {:label-key :common/raw-label}, :line_total {:label-key :common/line-total}, :article_id {:label-key :common/article-id}, :expense_purchased_at {:label-key :common/purchased-at}, :alias_id {:label-key :common/alias-id}, :article_canonical_name {:label-key :common/article}, :qty {:label-key :common/qty}, :expense_id {:label-key :common/expense-id}, :created_at {:label-key :common/created-at}}}, :suppliers {:available-columns ["display_name" "normalized_key" "store_count" "created_at" "id" "updated_at"], :default-visible-columns ["display_name" "normalized_key" "store_count" "created_at"], :filterable-columns ["display_name" "normalized_key" "created_at"], :sortable-columns ["display_name" "normalized_key" "store_count" "created_at"], :always-visible ["display_name"], :computed-fields {:store_count {:label-key :common/stores, :type :number, :admin {:visible-in-table? true, :filterable? false, :sortable? true}}}, :column-metadata {:display_name {:label-key :common/supplier-name}, :normalized_key {:label-key :common/normalized-key}, :store_count {:label-key :common/stores}, :created_at {:label-key :common/created-at}, :id {:label-key :common/id}, :updated_at {:label-key :common/updated-at}}}, :subcategories {:available-columns ["category_name" "name" "description" "is_active" "is_system_default" "created_at" "category_id" "updated_at" "id"], :default-visible-columns ["name" "category_name" "description" "is_active" "created_at"], :filterable-columns ["name" "category_name" "description" "is_active" "created_at"], :sortable-columns ["name" "category_name" "is_active" "is_system_default" "created_at" "updated_at"], :always-visible ["name"], :computed-fields {:category_name {:type "string", :label "Category"}}, :column-config {:category_id {:type "text"}, :is_active {:type "boolean"}, :is_system_default {:type "boolean"}}, :column-metadata {:description {:label-key :common/description}, :name {:label-key :common/subcategory-name}, :is_active {:label "Active"}, :updated_at {:label-key :common/updated-at}, :category_name {:label-key :common/category}, :id {:label-key :common/id}, :category_id {:label-key :common/category-id}, :created_at {:label-key :common/created-at}, :is_system_default {:label "System default"}}}, :cities {:available-columns ["name" "normalized_key" "zip" "country" "created_at" "updated_at" "id"], :default-visible-columns ["name" "country" "zip" "normalized_key" "created_at"], :filterable-columns ["name" "normalized_key" "zip" "country" "created_at"], :sortable-columns ["name" "normalized_key" "zip" "country" "created_at" "updated_at"], :always-visible ["name"], :column-metadata {:name {:label-key :common/city-name}, :normalized_key {:label-key :common/normalized-key}, :zip {:label-key :common/zip}, :country {:label-key :common/country}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}}, :articles {:available-columns ["canonical_name" "unit" "category_name" "subcategory_id" "subcategory_name" "manufacturer_display_name" "manufacturer_id" "link" "normalized_key" "created_at" "updated_at" "id"], :default-visible-columns ["canonical_name" "unit" "category_name" "subcategory_name" "manufacturer_display_name" "created_at"], :filterable-columns ["canonical_name" "unit" "category_name" "subcategory_name" "manufacturer_display_name" "link" "normalized_key" "created_at" "updated_at"], :sortable-columns ["canonical_name" "unit" "category_name" "subcategory_name" "manufacturer_display_name" "link" "normalized_key" "created_at" "updated_at"], :always-visible ["canonical_name"], :computed-fields {:manufacturer_display_name {:type "string", :label "Manufacturer"}, :category_name {:type "string", :label "Category"}, :subcategory_name {:type "string", :label "Subcategory"}}, :column-config {:subcategory_id {:type "text"}, :manufacturer_id {:type "text"}}, :column-metadata {:unit {:label-key :common/unit}, :manufacturer_display_name {:label-key :common/manufacturer}, :manufacturer_id {:label-key :common/manufacturer-id}, :normalized_key {:label-key :common/normalized-key}, :updated_at {:label-key :common/updated-at}, :category_name {:label-key :common/category}, :link {:label-key :common/link}, :id {:label-key :common/id}, :subcategory_name {:label-key :common/subcategory}, :canonical_name {:label-key :common/article}, :subcategory_id {:label-key :common/subcategory-id}, :created_at {:label-key :common/created-at}}}, :expenses {:available-columns ["purchased_at" "supplier_display_name" "store_display_name" "expense_category_name" "expense_context_name" "payer_label" "currency" "total_amount" "item_count" "notes" "id" "created_at" "updated_at" "tenant_id" "receipt_id" "store_id" "supplier_id" "payer_id" "expense_category_id" "expense_context_id"], :computed-fields {:supplier_display_name {:type "string", :label "Supplier"}, :store_display_name {:type "string", :label "Store"}, :expense_category_name {:type "string", :label "Expense Category"}, :expense_context_name {:type "string", :label "Expense Context"}, :item_count {:label-key :common/items, :type :number, :admin {:visible-in-table? true, :filterable? false, :sortable? true}}, :payer_label {:type "string", :label "Payer"}}, :default-visible-columns ["purchased_at" "supplier_display_name" "store_display_name" "expense_category_name" "expense_context_name" "payer_label" "currency" "total_amount" "item_count"], :filterable-columns ["purchased_at" "supplier_display_name" "store_display_name" "expense_category_name" "expense_context_name" "payer_label" "currency" "total_amount" "created_at" "updated_at"], :sortable-columns ["purchased_at" "supplier_display_name" "store_display_name" "expense_category_name" "expense_context_name" "payer_label" "currency" "total_amount" "item_count" "created_at" "updated_at"], :always-visible [], :column-config {:receipt_id {:type "text"}, :supplier_id {:type "text"}, :payer_id {:type "text"}, :store_id {:type "text"}, :expense_category_id {:type "text"}, :expense_context_id {:type "text"}}, :column-metadata {:expense_category_name {:label-key :common/expense-category}, :payer_label {:label-key :common/payer}, :receipt_id {:label-key :common/receipt-id}, :item_count {:label-key :common/items}, :supplier_display_name {:label-key :common/supplier}, :payer_id {:label-key :common/payer-id}, :updated_at {:label-key :common/updated-at}, :currency {:label-key :common/currency}, :expense_category_id {:label-key :common/expense-category-id}, :store_display_name {:label-key :common/store}, :supplier_id {:label-key :common/supplier-id}, :id {:label-key :common/id}, :expense_context_name {:label-key :common/expense-context}, :notes {:label-key :common/notes}, :purchased_at {:label-key :common/purchased-at}, :total_amount {:label-key :common/total}, :store_id {:label-key :common/store-id}, :created_at {:label-key :common/created-at}, :expense_context_id {:label-key :common/expense-context-id}}}, :manufacturers {:available-columns ["display_name" "normalized_key" "created_at" "updated_at" "id"], :default-visible-columns ["display_name" "normalized_key" "created_at"], :filterable-columns ["display_name" "normalized_key" "created_at"], :sortable-columns ["display_name" "normalized_key" "created_at"], :always-visible ["display_name"], :column-metadata {:display_name {:label-key :common/manufacturer-name}, :normalized_key {:label-key :common/normalized-key}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}}, :unmapped-aliases {:available-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "unit" "occurrence_count" "supplier_id" "id"], :default-visible-columns ["supplier_display_name" "raw_label" "unit" "occurrence_count"], :filterable-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "unit" "occurrence_count"], :sortable-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "unit" "occurrence_count"], :always-visible ["raw_label"], :computed-fields {:supplier_display_name {}}, :column-config {:supplier_id {:type "text"}}, :column-metadata {:supplier_display_name {:label-key :common/supplier}, :raw_label {:label-key :common/raw-label}, :raw_label_normalized {:label-key :common/normalized}, :unit {:label-key :common/unit}, :occurrence_count {:label-key :common/occurrences}, :supplier_id {:label-key :common/supplier-id}, :id {:label-key :common/id}}}, :countries {:available-columns ["country" "code" "created_at" "updated_at" "id"], :default-visible-columns ["country" "code"], :filterable-columns ["country" "code"], :sortable-columns ["country" "code" "created_at" "updated_at"], :always-visible ["country"], :computed-fields {:id {:type "string"}}, :column-metadata {:country {:label-key :common/country}, :code {:label-key :common/code}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}}, :payers {:available-columns ["label" "is_default" "user_full_name" "user_email" "created_at" "updated_at" "id" "tenant_id"], :default-visible-columns ["label" "is_default" "user_full_name"], :filterable-columns ["label" "is_default" "user_full_name"], :sortable-columns ["label" "is_default" "user_full_name"], :always-visible ["label"], :computed-fields {:user_email {:type "string", :label "Email"}, :user_full_name {:type "string", :label "User"}}, :column-metadata {:label {:label-key :common/label}, :is_default {:label-key :common/is-default}, :user_full_name {:label "User"}, :user_email {:label "Email"}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}}, :categories {:available-columns ["name" "description" "created_at" "updated_at" "id"], :default-visible-columns ["name" "description" "created_at"], :filterable-columns ["name" "description" "created_at"], :sortable-columns ["name" "description" "created_at" "updated_at"], :always-visible ["name"], :column-metadata {:name {:label-key :common/category-name}, :description {:label-key :common/description}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}}, :supplier-aliases {:available-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "confidence" "created_at" "updated_at" "supplier_id" "id"], :default-visible-columns ["supplier_display_name" "raw_label" "raw_label_normalized"], :filterable-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "confidence" "created_at"], :sortable-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "confidence" "created_at"], :always-visible ["raw_label"], :computed-fields {:supplier_display_name {}}, :column-config {:supplier_id {:type "text"}}, :column-metadata {:supplier_display_name {:label-key :common/supplier}, :raw_label {:label-key :common/raw-label}, :raw_label_normalized {:label-key :common/normalized}, :confidence {:label-key :common/confidence}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :supplier_id {:label-key :common/supplier-id}, :id {:label-key :common/id}}}, :receipts {:available-columns ["status" "supplier-guess" "created-at" "updated-at" "id" "file-size" "raw-extract-json" "parsed-markdown" "total-amount-guess" "currency-guess" "purchased-at-guess" "error-message" "error-details" "retry-count" "expense-id" "payer-id" "storage-key" "file-hash" "content-type" "supplier-alias-id" "store-guess" "store-alias-id" "tenant-id"], :default-visible-columns ["status" "supplier-guess" "created-at" "updated-at"], :filterable-columns ["status" "supplier-guess" "created-at" "updated-at"], :sortable-columns ["status" "supplier-guess" "created-at" "updated-at"], :always-visible ["status" "purchased-at-guess"], :computed-fields {}, :column-config {:expense-id {:type "text"}, :payer-id {:type "text"}, :supplier-alias-id {:type "text"}, :store-alias-id {:type "text"}}, :column-metadata {:raw-extract-json {:label-key :common/raw-extract-json}, :updated-at {:label-key :common/updated-at}, :total-amount-guess {:label-key :common/total-amount-guess}, :purchased-at-guess {:label-key :common/purchased-at-guess}, :file-size {:label-key :common/file-size}, :store-alias-id {:label-key :common/store-alias}, :currency-guess {:label-key :common/currency-guess}, :store-guess {:label-key :common/store-guess}, :expense-id {:label-key :common/expense-id}, :status {:label-key :common/status}, :file-hash {:label-key :common/file-hash}, :payer-id {:label-key :common/payer-id}, :id {:label-key :common/id}, :content-type {:label-key :common/content-type}, :supplier-alias-id {:label-key :common/supplier-alias}, :error-details {:label-key :common/error-details}, :storage-key {:label-key :common/storage-key}, :error-message {:label-key :common/error-message}, :supplier-guess {:label-key :common/supplier-guess}, :retry-count {:label-key :common/retry-count}, :created-at {:label-key :common/created-at}, :parsed-markdown {:label-key :common/parsed-markdown}}}, :stores {:available-columns ["supplier_display_name" "display_name" "normalized_key" "address" "city_name" "created_at" "supplier_id" "city_id" "updated_at" "id"], :default-visible-columns ["display_name" "supplier_display_name" "normalized_key" "address" "city_name" "created_at"], :filterable-columns ["display_name" "supplier_display_name" "normalized_key" "address" "city_name" "created_at"], :sortable-columns ["display_name" "supplier_display_name" "normalized_key" "address" "city_name" "created_at" "updated_at"], :always-visible ["display_name"], :computed-fields {:supplier_display_name {:type "string", :label "Supplier"}, :city_name {:type "string", :label "City"}}, :column-config {:supplier_id {:type "text"}, :city_id {:type "text"}}, :column-metadata {:address {:label-key :common/address}, :supplier_display_name {:label-key :common/supplier}, :normalized_key {:label-key :common/normalized-key}, :city_name {:label-key :common/city}, :updated_at {:label-key :common/updated-at}, :supplier_id {:label-key :common/supplier-id}, :id {:label-key :common/id}, :city_id {:label-key :common/city-id}, :display_name {:label-key :common/store-name}, :created_at {:label-key :common/created-at}}}, :article-aliases {:available-columns ["supplier_display_name" "article_canonical_name" "raw_label" "raw_label_normalized" "unit" "confidence" "created_at" "supplier_id" "article_id" "id" "updated_at"], :default-visible-columns ["supplier_display_name" "raw_label" "unit" "article_canonical_name"], :filterable-columns ["supplier_display_name" "article_canonical_name" "raw_label" "raw_label_normalized" "unit" "confidence" "created_at"], :sortable-columns ["supplier_display_name" "article_canonical_name" "raw_label" "raw_label_normalized" "unit" "confidence" "created_at"], :always-visible ["raw_label"], :computed-fields {:supplier_display_name {}, :article_canonical_name {}}, :column-config {:supplier_id {:type "text"}, :article_id {:type "text"}}, :column-metadata {:unit {:label-key :common/unit}, :raw_label {:label-key :common/raw-label}, :raw_label_normalized {:label-key :common/normalized}, :supplier_display_name {:label-key :common/supplier}, :article_id {:label-key :common/article-id}, :supplier_id {:label-key :common/supplier-id}, :id {:label-key :common/id}, :article_canonical_name {:label-key :common/article}, :confidence {:label-key :common/confidence}, :created_at {:label-key :common/created-at}}}, :store-aliases {:available-columns ["supplier_display_name" "store_display_name" "store_address" "raw_label" "raw_label_normalized" "confidence" "created_at" "updated_at" "store_id" "id"], :default-visible-columns ["supplier_display_name" "raw_label" "raw_label_normalized" "store_display_name" "confidence" "created_at"], :filterable-columns ["supplier_display_name" "store_display_name" "store_address" "raw_label" "raw_label_normalized" "confidence" "created_at"], :sortable-columns ["supplier_display_name" "store_display_name" "raw_label" "raw_label_normalized" "confidence" "created_at"], :always-visible ["raw_label"], :computed-fields {:supplier_display_name {}, :store_display_name {}, :store_address {}}, :column-config {:store_id {:type "text"}}, :column-metadata {:store_address {:label-key :common/address}, :raw_label {:label-key :common/raw-label}, :raw_label_normalized {:label-key :common/normalized}, :supplier_display_name {:label-key :common/supplier}, :updated_at {:label-key :common/updated-at}, :store_display_name {:label-key :common/store}, :id {:label-key :common/id}, :confidence {:label-key :common/confidence}, :store_id {:label-key :common/store-id}, :created_at {:label-key :common/created-at}}}, :expense-categories {:available-columns ["name" "exclude_from_reports" "is_default" "created_at" "updated_at" "id" "tenant_id"], :default-visible-columns ["name" "is_default" "exclude_from_reports" "created_at"], :filterable-columns ["name" "is_default" "exclude_from_reports" "created_at"], :sortable-columns ["name" "is_default" "exclude_from_reports" "created_at" "updated_at"], :always-visible ["name"], :column-metadata {:name {:label-key :common/expense-category-name}, :exclude_from_reports {:label "Exclude from reports"}, :is_default {:label-key :common/is-default}, :created_at {:label-key :common/created-at}, :updated_at {:label-key :common/updated-at}, :id {:label-key :common/id}}, :computed-fields {}, :column-config {}}}	2026-09-24 18:58:01.268869+00	2026-09-24 18:58:01.376095+00
\.


--
-- TOC entry 4002 (class 0 OID 17541)
-- Dependencies: 241
-- Data for Name: global_settings; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.global_settings (id, default_currency, default_note, auto_publish_after_upload, ai_receipt_enhancement, created_at, updated_at) FROM stdin;
d24a3b78-17d8-46c8-9a50-594348b38e98	BAM	\N	f	f	2026-09-24 18:44:50.199675+00	2026-09-24 18:44:50.199675+00
\.


--
-- TOC entry 3991 (class 0 OID 17005)
-- Dependencies: 230
-- Data for Name: login_events; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.login_events (updated_at, ip, user_agent, principal_type, reason, success, id, created_at, principal_id) FROM stdin;
2026-09-25 14:09:14.221488+00	0:0:0:0:0:0:0:1	curl/8.7.1	admin	invalid_credentials	f	227286bb-5887-44dd-bf0f-369eb3761b16	2026-09-25 14:09:14.218531+00	77e9697e-3564-4269-9985-61e95746d8e0
2026-09-25 14:11:02.869687+00	0:0:0:0:0:0:0:1	curl/8.7.1	admin	\N	t	f809b098-cb8e-4eda-8dec-9b177cc1a878	2026-09-25 14:11:02.869597+00	77e9697e-3564-4269-9985-61e95746d8e0
2026-09-25 14:11:36.937573+00	0:0:0:0:0:0:0:1	Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36	admin	\N	t	11be29ba-78ab-4db9-bfaf-c07cfa72f0bd	2026-09-25 14:11:36.936563+00	77e9697e-3564-4269-9985-61e95746d8e0
2026-09-25 14:20:19.565064+00	0:0:0:0:0:0:0:1	curl/8.7.1	admin	\N	t	4c74f854-7212-4cf2-851a-51888cf80b20	2026-09-25 14:20:19.563445+00	77e9697e-3564-4269-9985-61e95746d8e0
2026-09-25 14:20:52.095017+00	0:0:0:0:0:0:0:1	curl/8.7.1	admin	\N	t	da4abb37-666a-495e-9f0e-e731ebecbf32	2026-09-25 14:20:52.094338+00	77e9697e-3564-4269-9985-61e95746d8e0
2026-09-26 07:37:36.96903+00	0:0:0:0:0:0:0:1	Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36	admin	\N	t	fa27048d-140e-4d59-8007-1a9bbef06ce0	2026-09-26 07:37:36.968494+00	77e9697e-3564-4269-9985-61e95746d8e0
\.


--
-- TOC entry 3984 (class 0 OID 16868)
-- Dependencies: 223
-- Data for Name: manufacturers; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.manufacturers (id, display_name, normalized_key, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 4006 (class 0 OID 17649)
-- Dependencies: 245
-- Data for Name: onboarding_progress; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.onboarding_progress (id, user_id, role, dismissed, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 4007 (class 0 OID 17689)
-- Dependencies: 246
-- Data for Name: onboarding_steps; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.onboarding_steps (id, progress_id, kind, step_order, status, completed_at, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3995 (class 0 OID 17098)
-- Dependencies: 234
-- Data for Name: password_reset_tokens; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.password_reset_tokens (id, principal_type, principal_id, token, expires_at, used_at, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3979 (class 0 OID 16687)
-- Dependencies: 218
-- Data for Name: payers; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.payers (id, tenant_id, label, is_default, created_at, updated_at, is_active, type) FROM stdin;
\.


--
-- TOC entry 4008 (class 0 OID 17725)
-- Dependencies: 247
-- Data for Name: payment_provider_account_links; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.payment_provider_account_links (id, account_kind, account_id, provider, provider_customer_ref, status, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3997 (class 0 OID 17166)
-- Dependencies: 236
-- Data for Name: raw_labels; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.raw_labels (id, raw_label, normalized_key, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3980 (class 0 OID 16705)
-- Dependencies: 219
-- Data for Name: receipts; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.receipts (original_filename, raw_extract_json, updated_at, total_amount_guess, purchased_at_guess, file_size, store_alias_id, currency_guess, store_guess, expense_id, status, file_hash, payer_id, id, content_type, supplier_alias_id, error_details, storage_key, error_message, supplier_guess, retry_count, created_at, tenant_id, parsed_markdown, expense_category_id, notes, file_purged_at, subject_ref, created_by_subject_ref) FROM stdin;
\.


--
-- TOC entry 4012 (class 0 OID 17878)
-- Dependencies: 251
-- Data for Name: recurring_expense_reminders; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.recurring_expense_reminders (updated_at, skipped_at, status, snoozed_until, id, due_date, recorded_expense_id, created_at, reminded_at, template_id) FROM stdin;
\.


--
-- TOC entry 3976 (class 0 OID 16576)
-- Dependencies: 215
-- Data for Name: store_aliases; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.store_aliases (id, raw_label, raw_label_normalized, store_id, confidence, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3975 (class 0 OID 16556)
-- Dependencies: 214
-- Data for Name: stores; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.stores (id, supplier_id, display_name, normalized_key, address, city_id, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3986 (class 0 OID 16888)
-- Dependencies: 225
-- Data for Name: subcategories; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.subcategories (id, category_id, name, description, created_at, updated_at, is_system_default, tenant_id, created_by_subject_ref, is_active) FROM stdin;
c55b079e-0c11-46da-b410-9788e9f091cc	eb40db31-554d-432b-a507-01d1c9a229fc	Heating	Heating and district heating bills.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00	t	\N	\N	t
b7cb6485-92f1-4bd0-afd9-decfb0edcffb	eb40db31-554d-432b-a507-01d1c9a229fc	Gas	Gas utility bills.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00	t	\N	\N	t
9ba13502-b589-47ff-8a18-9feebd086259	eb40db31-554d-432b-a507-01d1c9a229fc	Water	Water supply and related utility costs.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00	t	\N	\N	t
43cc8944-72c2-4d5c-9a6a-a684c896f995	eb40db31-554d-432b-a507-01d1c9a229fc	Electricity	Electricity bills and power usage.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00	t	\N	\N	t
558f787a-d5fe-4c6e-aedd-d0c49f16aa97	8da33e1c-1a82-4545-924c-2fc7be4cf329	Donations	Recurring donations and supporter payments.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00	t	\N	\N	t
d70ea1b9-fa5b-4e86-bf58-9efe43bb04eb	8da33e1c-1a82-4545-924c-2fc7be4cf329	Memberships	Membership and club subscriptions.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00	t	\N	\N	t
3c9fcfda-7bb7-4aaa-9f68-8e5a974a9a39	8da33e1c-1a82-4545-924c-2fc7be4cf329	Software	Software-as-a-service and app subscriptions.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00	t	\N	\N	t
dd91fa4f-5a64-4560-950c-77c142d4bb11	8da33e1c-1a82-4545-924c-2fc7be4cf329	AI tools	AI assistants and AI service subscriptions.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00	t	\N	\N	t
b2a1f14e-6438-401c-8d8f-4c0269461a2b	8da33e1c-1a82-4545-924c-2fc7be4cf329	Hosting	Web hosting, domains, and infrastructure subscriptions.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00	t	\N	\N	t
89b0ec09-714b-475d-a218-b7d9e48637d3	8da33e1c-1a82-4545-924c-2fc7be4cf329	Internet	Internet service subscriptions.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00	t	\N	\N	t
77387793-22a8-4900-8cb3-8bd75564fa9c	8da33e1c-1a82-4545-924c-2fc7be4cf329	Mobile	Mobile phone plans and mobile services.	2026-09-24 18:57:27.457217+00	2026-09-24 18:57:27.457217+00	t	\N	\N	t
\.


--
-- TOC entry 3978 (class 0 OID 16663)
-- Dependencies: 217
-- Data for Name: supplier_aliases; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.supplier_aliases (id, raw_label, raw_label_normalized, supplier_id, confidence, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3972 (class 0 OID 16523)
-- Dependencies: 211
-- Data for Name: suppliers; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.suppliers (id, display_name, normalized_key, address, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3971 (class 0 OID 16501)
-- Dependencies: 210
-- Data for Name: tenant_invitations; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.tenant_invitations (role, updated_at, email, expires_at, invited_by, token, status, id, created_at, tenant_id, email_lookup_hash, email_key_version, email_ciphertext) FROM stdin;
\.


--
-- TOC entry 3977 (class 0 OID 16607)
-- Dependencies: 216
-- Data for Name: tenant_memberships; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.tenant_memberships (id, tenant_id, user_id, role, status, invited_by, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 4003 (class 0 OID 17554)
-- Dependencies: 242
-- Data for Name: tenant_settings; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.tenant_settings (id, tenant_id, email_notifications, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3970 (class 0 OID 16483)
-- Dependencies: 209
-- Data for Name: tenants; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.tenants (id, name, slug, status, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3992 (class 0 OID 17015)
-- Dependencies: 231
-- Data for Name: user_expense_settings; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.user_expense_settings (updated_at, receipt_ocr_provider, id, default_payer_id, created_at, tenant_id, subject_ref) FROM stdin;
\.


--
-- TOC entry 3993 (class 0 OID 17048)
-- Dependencies: 232
-- Data for Name: user_sessions; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.user_sessions (id, session_key, data, expires_at, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 3969 (class 0 OID 16449)
-- Dependencies: 208
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: app_user
--

COPY public.users (updated_at, email, avatar_url, password_hash, last_login_at, status, id, email_verified, full_name, auth_provider, created_at, email_ciphertext, email_lookup_hash, email_key_version) FROM stdin;
\.


--
-- TOC entry 4025 (class 0 OID 0)
-- Dependencies: 204
-- Name: automigrate_migrations_id_seq; Type: SEQUENCE SET; Schema: public; Owner: app_user
--

SELECT pg_catalog.setval('public.automigrate_migrations_id_seq', 79, true);


--
-- TOC entry 4026 (class 0 OID 0)
-- Dependencies: 237
-- Name: backlog_number_seq; Type: SEQUENCE SET; Schema: public; Owner: app_user
--

SELECT pg_catalog.setval('public.backlog_number_seq', 1, false);


--
-- TOC entry 3699 (class 2606 OID 17497)
-- Name: admin_invitations admin_invitations_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.admin_invitations
    ADD CONSTRAINT admin_invitations_pkey PRIMARY KEY (id);


--
-- TOC entry 3548 (class 2606 OID 16433)
-- Name: admin_sessions admin_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.admin_sessions
    ADD CONSTRAINT admin_sessions_pkey PRIMARY KEY (id);


--
-- TOC entry 3550 (class 2606 OID 16435)
-- Name: admin_sessions admin_sessions_token_key; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.admin_sessions
    ADD CONSTRAINT admin_sessions_token_key UNIQUE (token);


--
-- TOC entry 3541 (class 2606 OID 16423)
-- Name: admins admins_email_key; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.admins
    ADD CONSTRAINT admins_email_key UNIQUE (email);


--
-- TOC entry 3543 (class 2606 OID 16421)
-- Name: admins admins_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.admins
    ADD CONSTRAINT admins_pkey PRIMARY KEY (id);


--
-- TOC entry 3655 (class 2606 OID 16938)
-- Name: article_aliases article_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.article_aliases
    ADD CONSTRAINT article_aliases_pkey PRIMARY KEY (id);


--
-- TOC entry 3650 (class 2606 OID 16912)
-- Name: articles articles_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT articles_pkey PRIMARY KEY (id);


--
-- TOC entry 3680 (class 2606 OID 17076)
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- TOC entry 3537 (class 2606 OID 16395)
-- Name: automigrate_migrations automigrate_migrations_name_key; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.automigrate_migrations
    ADD CONSTRAINT automigrate_migrations_name_key UNIQUE (name);


--
-- TOC entry 3539 (class 2606 OID 16393)
-- Name: automigrate_migrations automigrate_migrations_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.automigrate_migrations
    ADD CONSTRAINT automigrate_migrations_pkey PRIMARY KEY (id);


--
-- TOC entry 3691 (class 2606 OID 17151)
-- Name: backlog backlog_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.backlog
    ADD CONSTRAINT backlog_pkey PRIMARY KEY (number);


--
-- TOC entry 3641 (class 2606 OID 16887)
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- TOC entry 3579 (class 2606 OID 16550)
-- Name: cities cities_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.cities
    ADD CONSTRAINT cities_pkey PRIMARY KEY (id);


--
-- TOC entry 3576 (class 2606 OID 16539)
-- Name: countries countries_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.countries
    ADD CONSTRAINT countries_pkey PRIMARY KEY (country);


--
-- TOC entry 3704 (class 2606 OID 17539)
-- Name: daily_exchange_rates daily_exchange_rates_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.daily_exchange_rates
    ADD CONSTRAINT daily_exchange_rates_pkey PRIMARY KEY (id);


--
-- TOC entry 3665 (class 2606 OID 16987)
-- Name: dedup_ignored_clusters dedup_ignored_clusters_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.dedup_ignored_clusters
    ADD CONSTRAINT dedup_ignored_clusters_pkey PRIMARY KEY (id);


--
-- TOC entry 3630 (class 2606 OID 16859)
-- Name: email_verification_tokens email_verification_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_pkey PRIMARY KEY (id);


--
-- TOC entry 3632 (class 2606 OID 16861)
-- Name: email_verification_tokens email_verification_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_token_key UNIQUE (token);


--
-- TOC entry 3712 (class 2606 OID 17574)
-- Name: enabled_currencies enabled_currencies_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.enabled_currencies
    ADD CONSTRAINT enabled_currencies_pkey PRIMARY KEY (id);


--
-- TOC entry 3715 (class 2606 OID 17585)
-- Name: exchange_rate_fetch_alerts exchange_rate_fetch_alerts_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.exchange_rate_fetch_alerts
    ADD CONSTRAINT exchange_rate_fetch_alerts_pkey PRIMARY KEY (id);


--
-- TOC entry 3615 (class 2606 OID 16799)
-- Name: expense_categories expense_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_categories
    ADD CONSTRAINT expense_categories_pkey PRIMARY KEY (id);


--
-- TOC entry 3734 (class 2606 OID 17765)
-- Name: expense_contexts expense_contexts_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_contexts
    ADD CONSTRAINT expense_contexts_pkey PRIMARY KEY (id);


--
-- TOC entry 3659 (class 2606 OID 16955)
-- Name: expense_items expense_items_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_items
    ADD CONSTRAINT expense_items_pkey PRIMARY KEY (id);


--
-- TOC entry 3745 (class 2606 OID 17854)
-- Name: expense_template_lines expense_template_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_template_lines
    ADD CONSTRAINT expense_template_lines_pkey PRIMARY KEY (id);


--
-- TOC entry 3739 (class 2606 OID 17811)
-- Name: expense_templates expense_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_templates
    ADD CONSTRAINT expense_templates_pkey PRIMARY KEY (id);


--
-- TOC entry 3619 (class 2606 OID 16816)
-- Name: expenses expenses_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_pkey PRIMARY KEY (id);


--
-- TOC entry 3696 (class 2606 OID 17455)
-- Name: frontend_runtime_configs frontend_runtime_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.frontend_runtime_configs
    ADD CONSTRAINT frontend_runtime_configs_pkey PRIMARY KEY (id);


--
-- TOC entry 3707 (class 2606 OID 17553)
-- Name: global_settings global_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.global_settings
    ADD CONSTRAINT global_settings_pkey PRIMARY KEY (id);


--
-- TOC entry 3671 (class 2606 OID 17014)
-- Name: login_events login_events_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.login_events
    ADD CONSTRAINT login_events_pkey PRIMARY KEY (id);


--
-- TOC entry 3639 (class 2606 OID 16877)
-- Name: manufacturers manufacturers_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.manufacturers
    ADD CONSTRAINT manufacturers_pkey PRIMARY KEY (id);


--
-- TOC entry 3720 (class 2606 OID 17656)
-- Name: onboarding_progress onboarding_progress_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.onboarding_progress
    ADD CONSTRAINT onboarding_progress_pkey PRIMARY KEY (id);


--
-- TOC entry 3725 (class 2606 OID 17696)
-- Name: onboarding_steps onboarding_steps_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.onboarding_steps
    ADD CONSTRAINT onboarding_steps_pkey PRIMARY KEY (id);


--
-- TOC entry 3687 (class 2606 OID 17104)
-- Name: password_reset_tokens password_reset_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);


--
-- TOC entry 3689 (class 2606 OID 17106)
-- Name: password_reset_tokens password_reset_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_token_key UNIQUE (token);


--
-- TOC entry 3604 (class 2606 OID 16694)
-- Name: payers payers_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.payers
    ADD CONSTRAINT payers_pkey PRIMARY KEY (id);


--
-- TOC entry 3730 (class 2606 OID 17735)
-- Name: payment_provider_account_links payment_provider_account_links_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.payment_provider_account_links
    ADD CONSTRAINT payment_provider_account_links_pkey PRIMARY KEY (id);


--
-- TOC entry 3694 (class 2606 OID 17175)
-- Name: raw_labels raw_labels_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.raw_labels
    ADD CONSTRAINT raw_labels_pkey PRIMARY KEY (id);


--
-- TOC entry 3613 (class 2606 OID 16716)
-- Name: receipts receipts_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.receipts
    ADD CONSTRAINT receipts_pkey PRIMARY KEY (id);


--
-- TOC entry 3752 (class 2606 OID 17886)
-- Name: recurring_expense_reminders recurring_expense_reminders_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.recurring_expense_reminders
    ADD CONSTRAINT recurring_expense_reminders_pkey PRIMARY KEY (id);


--
-- TOC entry 3590 (class 2606 OID 16586)
-- Name: store_aliases store_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.store_aliases
    ADD CONSTRAINT store_aliases_pkey PRIMARY KEY (id);


--
-- TOC entry 3586 (class 2606 OID 16565)
-- Name: stores stores_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.stores
    ADD CONSTRAINT stores_pkey PRIMARY KEY (id);


--
-- TOC entry 3646 (class 2606 OID 16897)
-- Name: subcategories subcategories_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.subcategories
    ADD CONSTRAINT subcategories_pkey PRIMARY KEY (id);


--
-- TOC entry 3600 (class 2606 OID 16673)
-- Name: supplier_aliases supplier_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.supplier_aliases
    ADD CONSTRAINT supplier_aliases_pkey PRIMARY KEY (id);


--
-- TOC entry 3574 (class 2606 OID 16532)
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (id);


--
-- TOC entry 3571 (class 2606 OID 16511)
-- Name: tenant_invitations tenant_invitations_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.tenant_invitations
    ADD CONSTRAINT tenant_invitations_pkey PRIMARY KEY (id);


--
-- TOC entry 3594 (class 2606 OID 16614)
-- Name: tenant_memberships tenant_memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.tenant_memberships
    ADD CONSTRAINT tenant_memberships_pkey PRIMARY KEY (id);


--
-- TOC entry 3709 (class 2606 OID 17561)
-- Name: tenant_settings tenant_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.tenant_settings
    ADD CONSTRAINT tenant_settings_pkey PRIMARY KEY (id);


--
-- TOC entry 3565 (class 2606 OID 16493)
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- TOC entry 3674 (class 2606 OID 17029)
-- Name: user_expense_settings user_expense_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.user_expense_settings
    ADD CONSTRAINT user_expense_settings_pkey PRIMARY KEY (id);


--
-- TOC entry 3678 (class 2606 OID 17057)
-- Name: user_sessions user_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT user_sessions_pkey PRIMARY KEY (id);


--
-- TOC entry 3559 (class 2606 OID 16464)
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- TOC entry 3561 (class 2606 OID 16462)
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- TOC entry 3700 (class 1259 OID 17716)
-- Name: idx_admin_invitations_email_lookup_hash; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_admin_invitations_email_lookup_hash ON public.admin_invitations USING btree (email_lookup_hash);


--
-- TOC entry 3701 (class 1259 OID 17509)
-- Name: idx_admin_invitations_status; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_admin_invitations_status ON public.admin_invitations USING btree (status);


--
-- TOC entry 3702 (class 1259 OID 17510)
-- Name: idx_admin_invitations_token; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_admin_invitations_token ON public.admin_invitations USING btree (token);


--
-- TOC entry 3551 (class 1259 OID 17061)
-- Name: idx_admin_sessions_admin_id; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_admin_sessions_admin_id ON public.admin_sessions USING btree (admin_id);


--
-- TOC entry 3552 (class 1259 OID 17114)
-- Name: idx_admin_sessions_expires; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_admin_sessions_expires ON public.admin_sessions USING btree (expires_at);


--
-- TOC entry 3553 (class 1259 OID 16441)
-- Name: idx_admin_sessions_token; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_admin_sessions_token ON public.admin_sessions USING btree (token);


--
-- TOC entry 3544 (class 1259 OID 17717)
-- Name: idx_admins_email_lookup_hash; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_admins_email_lookup_hash ON public.admins USING btree (email_lookup_hash);


--
-- TOC entry 3545 (class 1259 OID 17085)
-- Name: idx_admins_status; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_admins_status ON public.admins USING btree (status);


--
-- TOC entry 3656 (class 1259 OID 17078)
-- Name: idx_article_aliases_article; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_article_aliases_article ON public.article_aliases USING btree (article_id);


--
-- TOC entry 3657 (class 1259 OID 17713)
-- Name: idx_article_aliases_supplier_label; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_article_aliases_supplier_label ON public.article_aliases USING btree (supplier_id, raw_label_normalized, unit);


--
-- TOC entry 3651 (class 1259 OID 16923)
-- Name: idx_articles_manufacturer_id; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_articles_manufacturer_id ON public.articles USING btree (manufacturer_id);


--
-- TOC entry 3652 (class 1259 OID 17715)
-- Name: idx_articles_normalized_key; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_articles_normalized_key ON public.articles USING btree (normalized_key, unit);


--
-- TOC entry 3653 (class 1259 OID 17092)
-- Name: idx_articles_subcategory_id; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_articles_subcategory_id ON public.articles USING btree (subcategory_id);


--
-- TOC entry 3681 (class 1259 OID 17095)
-- Name: idx_audit_logs_actor; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_audit_logs_actor ON public.audit_logs USING btree (actor_type, actor_id);


--
-- TOC entry 3682 (class 1259 OID 17077)
-- Name: idx_audit_logs_created_at; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_audit_logs_created_at ON public.audit_logs USING btree (created_at);


--
-- TOC entry 3642 (class 1259 OID 16927)
-- Name: idx_categories_name; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_categories_name ON public.categories USING btree (name);


--
-- TOC entry 3580 (class 1259 OID 17165)
-- Name: idx_cities_normalized_key; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_cities_normalized_key ON public.cities USING btree (normalized_key);


--
-- TOC entry 3577 (class 1259 OID 17157)
-- Name: idx_countries_code; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_countries_code ON public.countries USING btree (code);


--
-- TOC entry 3666 (class 1259 OID 17059)
-- Name: idx_dedup_ignored_clusters_admin_entity; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_dedup_ignored_clusters_admin_entity ON public.dedup_ignored_clusters USING btree (admin_id, entity_type);


--
-- TOC entry 3633 (class 1259 OID 17153)
-- Name: idx_email_tokens_expires; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_email_tokens_expires ON public.email_verification_tokens USING btree (expires_at);


--
-- TOC entry 3634 (class 1259 OID 16867)
-- Name: idx_email_tokens_token; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_email_tokens_token ON public.email_verification_tokens USING btree (token);


--
-- TOC entry 3635 (class 1259 OID 17093)
-- Name: idx_email_tokens_user; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_email_tokens_user ON public.email_verification_tokens USING btree (user_id);


--
-- TOC entry 3716 (class 1259 OID 17588)
-- Name: idx_exchange_rate_fetch_alerts_date; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_exchange_rate_fetch_alerts_date ON public.exchange_rate_fetch_alerts USING btree (rate_date);


--
-- TOC entry 3717 (class 1259 OID 17586)
-- Name: idx_exchange_rate_fetch_alerts_unacknowledged; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_exchange_rate_fetch_alerts_unacknowledged ON public.exchange_rate_fetch_alerts USING btree (acknowledged) WHERE (acknowledged = false);


--
-- TOC entry 3616 (class 1259 OID 16977)
-- Name: idx_expense_categories_tenant_name; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_expense_categories_tenant_name ON public.expense_categories USING btree (tenant_id, name);


--
-- TOC entry 3735 (class 1259 OID 17899)
-- Name: idx_expense_contexts_tenant; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_contexts_tenant ON public.expense_contexts USING btree (tenant_id);


--
-- TOC entry 3736 (class 1259 OID 17838)
-- Name: idx_expense_contexts_tenant_active; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_contexts_tenant_active ON public.expense_contexts USING btree (tenant_id, is_active);


--
-- TOC entry 3737 (class 1259 OID 17839)
-- Name: idx_expense_contexts_tenant_name; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_expense_contexts_tenant_name ON public.expense_contexts USING btree (tenant_id, name);


--
-- TOC entry 3660 (class 1259 OID 17046)
-- Name: idx_expense_items_alias; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_items_alias ON public.expense_items USING btree (alias_id);


--
-- TOC entry 3661 (class 1259 OID 17047)
-- Name: idx_expense_items_article; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_items_article ON public.expense_items USING btree (article_id);


--
-- TOC entry 3662 (class 1259 OID 17152)
-- Name: idx_expense_items_expense; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_items_expense ON public.expense_items USING btree (expense_id);


--
-- TOC entry 3663 (class 1259 OID 17096)
-- Name: idx_expense_items_tenant; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_items_tenant ON public.expense_items USING btree (tenant_id);


--
-- TOC entry 3746 (class 1259 OID 17865)
-- Name: idx_expense_template_lines_article; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_template_lines_article ON public.expense_template_lines USING btree (article_id);


--
-- TOC entry 3747 (class 1259 OID 17898)
-- Name: idx_expense_template_lines_template_order; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_template_lines_template_order ON public.expense_template_lines USING btree (template_id, sort_order);


--
-- TOC entry 3740 (class 1259 OID 17900)
-- Name: idx_expense_templates_due; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_templates_due ON public.expense_templates USING btree (tenant_id, status, next_due_date);


--
-- TOC entry 3741 (class 1259 OID 17877)
-- Name: idx_expense_templates_tenant_kind; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_templates_tenant_kind ON public.expense_templates USING btree (tenant_id, kind);


--
-- TOC entry 3742 (class 1259 OID 17845)
-- Name: idx_expense_templates_tenant_name; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_templates_tenant_name ON public.expense_templates USING btree (tenant_id, name);


--
-- TOC entry 3743 (class 1259 OID 17837)
-- Name: idx_expense_templates_tenant_status; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expense_templates_tenant_status ON public.expense_templates USING btree (tenant_id, status);


--
-- TOC entry 3620 (class 1259 OID 17156)
-- Name: idx_expenses_expense_category; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expenses_expense_category ON public.expenses USING btree (expense_category_id);


--
-- TOC entry 3621 (class 1259 OID 17866)
-- Name: idx_expenses_expense_context; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expenses_expense_context ON public.expenses USING btree (expense_context_id);


--
-- TOC entry 3622 (class 1259 OID 17180)
-- Name: idx_expenses_payer; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expenses_payer ON public.expenses USING btree (payer_id);


--
-- TOC entry 3623 (class 1259 OID 17181)
-- Name: idx_expenses_purchased_at; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expenses_purchased_at ON public.expenses USING btree (purchased_at);


--
-- TOC entry 3624 (class 1259 OID 17178)
-- Name: idx_expenses_store; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expenses_store ON public.expenses USING btree (store_id);


--
-- TOC entry 3625 (class 1259 OID 17115)
-- Name: idx_expenses_supplier; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expenses_supplier ON public.expenses USING btree (supplier_id);


--
-- TOC entry 3626 (class 1259 OID 17084)
-- Name: idx_expenses_tenant; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expenses_tenant ON public.expenses USING btree (tenant_id);


--
-- TOC entry 3627 (class 1259 OID 17751)
-- Name: idx_expenses_tenant_subject; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_expenses_tenant_subject ON public.expenses USING btree (tenant_id, subject_ref);


--
-- TOC entry 3668 (class 1259 OID 17108)
-- Name: idx_login_events_created_at; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_login_events_created_at ON public.login_events USING btree (created_at);


--
-- TOC entry 3669 (class 1259 OID 17089)
-- Name: idx_login_events_principal; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_login_events_principal ON public.login_events USING btree (principal_type, principal_id);


--
-- TOC entry 3636 (class 1259 OID 17080)
-- Name: idx_manufacturers_display_name; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_manufacturers_display_name ON public.manufacturers USING btree (display_name);


--
-- TOC entry 3637 (class 1259 OID 17158)
-- Name: idx_manufacturers_normalized_key; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_manufacturers_normalized_key ON public.manufacturers USING btree (normalized_key);


--
-- TOC entry 3718 (class 1259 OID 17704)
-- Name: idx_onboarding_progress_user; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_onboarding_progress_user ON public.onboarding_progress USING btree (user_id);


--
-- TOC entry 3722 (class 1259 OID 17705)
-- Name: idx_onboarding_steps_progress; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_onboarding_steps_progress ON public.onboarding_steps USING btree (progress_id);


--
-- TOC entry 3723 (class 1259 OID 17702)
-- Name: idx_onboarding_steps_status; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_onboarding_steps_status ON public.onboarding_steps USING btree (status);


--
-- TOC entry 3683 (class 1259 OID 17107)
-- Name: idx_password_reset_tokens_expires; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_password_reset_tokens_expires ON public.password_reset_tokens USING btree (expires_at);


--
-- TOC entry 3684 (class 1259 OID 17127)
-- Name: idx_password_reset_tokens_principal; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_password_reset_tokens_principal ON public.password_reset_tokens USING btree (principal_type, principal_id);


--
-- TOC entry 3685 (class 1259 OID 17154)
-- Name: idx_password_reset_tokens_token; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_password_reset_tokens_token ON public.password_reset_tokens USING btree (token);


--
-- TOC entry 3601 (class 1259 OID 17097)
-- Name: idx_payers_tenant; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_payers_tenant ON public.payers USING btree (tenant_id);


--
-- TOC entry 3602 (class 1259 OID 17750)
-- Name: idx_payers_type; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_payers_type ON public.payers USING btree (type);


--
-- TOC entry 3727 (class 1259 OID 17736)
-- Name: idx_payment_provider_account_links_account; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_payment_provider_account_links_account ON public.payment_provider_account_links USING btree (account_kind, account_id);


--
-- TOC entry 3728 (class 1259 OID 17738)
-- Name: idx_payment_provider_account_links_status; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_payment_provider_account_links_status ON public.payment_provider_account_links USING btree (status);


--
-- TOC entry 3692 (class 1259 OID 17176)
-- Name: idx_raw_labels_normalized_key; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_raw_labels_normalized_key ON public.raw_labels USING btree (normalized_key);


--
-- TOC entry 3605 (class 1259 OID 16998)
-- Name: idx_receipts_created_at; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_receipts_created_at ON public.receipts USING btree (created_at);


--
-- TOC entry 3606 (class 1259 OID 17060)
-- Name: idx_receipts_status; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_receipts_status ON public.receipts USING btree (status);


--
-- TOC entry 3607 (class 1259 OID 17079)
-- Name: idx_receipts_store_alias_id; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_receipts_store_alias_id ON public.receipts USING btree (store_alias_id);


--
-- TOC entry 3608 (class 1259 OID 16996)
-- Name: idx_receipts_supplier_alias_id; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_receipts_supplier_alias_id ON public.receipts USING btree (supplier_alias_id);


--
-- TOC entry 3609 (class 1259 OID 16742)
-- Name: idx_receipts_tenant; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_receipts_tenant ON public.receipts USING btree (tenant_id);


--
-- TOC entry 3610 (class 1259 OID 17116)
-- Name: idx_receipts_tenant_file_hash; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_receipts_tenant_file_hash ON public.receipts USING btree (tenant_id, file_hash);


--
-- TOC entry 3611 (class 1259 OID 17752)
-- Name: idx_receipts_tenant_subject; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_receipts_tenant_subject ON public.receipts USING btree (tenant_id, subject_ref);


--
-- TOC entry 3748 (class 1259 OID 17902)
-- Name: idx_recurring_expense_reminders_recorded_expense; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_recurring_expense_reminders_recorded_expense ON public.recurring_expense_reminders USING btree (recorded_expense_id);


--
-- TOC entry 3749 (class 1259 OID 17903)
-- Name: idx_recurring_expense_reminders_snoozed_until; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_recurring_expense_reminders_snoozed_until ON public.recurring_expense_reminders USING btree (snoozed_until);


--
-- TOC entry 3750 (class 1259 OID 17901)
-- Name: idx_recurring_expense_reminders_template_status; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_recurring_expense_reminders_template_status ON public.recurring_expense_reminders USING btree (template_id, status);


--
-- TOC entry 3587 (class 1259 OID 17182)
-- Name: idx_store_aliases_raw_label_normalized; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_store_aliases_raw_label_normalized ON public.store_aliases USING btree (raw_label_normalized);


--
-- TOC entry 3588 (class 1259 OID 17113)
-- Name: idx_store_aliases_store_id; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_store_aliases_store_id ON public.store_aliases USING btree (store_id);


--
-- TOC entry 3582 (class 1259 OID 17161)
-- Name: idx_stores_city_id; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_stores_city_id ON public.stores USING btree (city_id);


--
-- TOC entry 3583 (class 1259 OID 17159)
-- Name: idx_stores_supplier; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_stores_supplier ON public.stores USING btree (supplier_id);


--
-- TOC entry 3584 (class 1259 OID 17109)
-- Name: idx_stores_supplier_normalized_key; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_stores_supplier_normalized_key ON public.stores USING btree (supplier_id, normalized_key);


--
-- TOC entry 3643 (class 1259 OID 17087)
-- Name: idx_subcategories_category_id; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_subcategories_category_id ON public.subcategories USING btree (category_id);


--
-- TOC entry 3644 (class 1259 OID 17910)
-- Name: idx_subcategories_tenant_id; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_subcategories_tenant_id ON public.subcategories USING btree (tenant_id);


--
-- TOC entry 3597 (class 1259 OID 17094)
-- Name: idx_supplier_aliases_raw_label_normalized; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_supplier_aliases_raw_label_normalized ON public.supplier_aliases USING btree (raw_label_normalized);


--
-- TOC entry 3598 (class 1259 OID 16926)
-- Name: idx_supplier_aliases_supplier_id; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_supplier_aliases_supplier_id ON public.supplier_aliases USING btree (supplier_id);


--
-- TOC entry 3572 (class 1259 OID 17155)
-- Name: idx_suppliers_normalized_key; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_suppliers_normalized_key ON public.suppliers USING btree (normalized_key);


--
-- TOC entry 3566 (class 1259 OID 17719)
-- Name: idx_tenant_invitations_email_lookup_hash; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_tenant_invitations_email_lookup_hash ON public.tenant_invitations USING btree (email_lookup_hash);


--
-- TOC entry 3567 (class 1259 OID 16522)
-- Name: idx_tenant_invitations_status; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_tenant_invitations_status ON public.tenant_invitations USING btree (status);


--
-- TOC entry 3568 (class 1259 OID 17112)
-- Name: idx_tenant_invitations_tenant; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_tenant_invitations_tenant ON public.tenant_invitations USING btree (tenant_id);


--
-- TOC entry 3569 (class 1259 OID 17163)
-- Name: idx_tenant_invitations_token; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_tenant_invitations_token ON public.tenant_invitations USING btree (token);


--
-- TOC entry 3591 (class 1259 OID 16997)
-- Name: idx_tenant_memberships_status; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_tenant_memberships_status ON public.tenant_memberships USING btree (status);


--
-- TOC entry 3592 (class 1259 OID 16978)
-- Name: idx_tenant_memberships_user; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_tenant_memberships_user ON public.tenant_memberships USING btree (user_id);


--
-- TOC entry 3562 (class 1259 OID 17088)
-- Name: idx_tenants_slug; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_tenants_slug ON public.tenants USING btree (slug);


--
-- TOC entry 3563 (class 1259 OID 16999)
-- Name: idx_tenants_status; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_tenants_status ON public.tenants USING btree (status);


--
-- TOC entry 3675 (class 1259 OID 17058)
-- Name: idx_user_sessions_expires; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_user_sessions_expires ON public.user_sessions USING btree (expires_at);


--
-- TOC entry 3676 (class 1259 OID 17091)
-- Name: idx_user_sessions_key; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_user_sessions_key ON public.user_sessions USING btree (session_key);


--
-- TOC entry 3554 (class 1259 OID 17183)
-- Name: idx_users_created_at; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_users_created_at ON public.users USING btree (created_at);


--
-- TOC entry 3555 (class 1259 OID 17718)
-- Name: idx_users_email_lookup_hash; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX idx_users_email_lookup_hash ON public.users USING btree (email_lookup_hash);


--
-- TOC entry 3556 (class 1259 OID 16924)
-- Name: idx_users_email_verified; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_users_email_verified ON public.users USING btree (email_verified);


--
-- TOC entry 3557 (class 1259 OID 17164)
-- Name: idx_users_status; Type: INDEX; Schema: public; Owner: app_user
--

CREATE INDEX idx_users_status ON public.users USING btree (status);


--
-- TOC entry 3546 (class 1259 OID 17511)
-- Name: uniq_admins_active_owner; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_admins_active_owner ON public.admins USING btree (role) WHERE ((role = 'owner'::public.admin_role) AND (status = 'active'::public.admin_status));


--
-- TOC entry 3581 (class 1259 OID 16925)
-- Name: uniq_cities_country_zip; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_cities_country_zip ON public.cities USING btree (country, zip);


--
-- TOC entry 3705 (class 1259 OID 17540)
-- Name: uniq_daily_exchange_rates_currency_date; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_daily_exchange_rates_currency_date ON public.daily_exchange_rates USING btree (currency_code, rate_date);


--
-- TOC entry 3667 (class 1259 OID 16993)
-- Name: uniq_dedup_ignored_clusters_admin_entity_cluster; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_dedup_ignored_clusters_admin_entity_cluster ON public.dedup_ignored_clusters USING btree (admin_id, entity_type, cluster_id);


--
-- TOC entry 3713 (class 1259 OID 17587)
-- Name: uniq_enabled_currencies_code; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_enabled_currencies_code ON public.enabled_currencies USING btree (code);


--
-- TOC entry 3617 (class 1259 OID 17742)
-- Name: uniq_expense_categories_tenant_default; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_expense_categories_tenant_default ON public.expense_categories USING btree (tenant_id, is_default) WHERE (is_default = true);


--
-- TOC entry 3628 (class 1259 OID 17711)
-- Name: uniq_expenses_receipt_id; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_expenses_receipt_id ON public.expenses USING btree (receipt_id);


--
-- TOC entry 3697 (class 1259 OID 17456)
-- Name: uniq_frontend_runtime_configs_scope_key; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_frontend_runtime_configs_scope_key ON public.frontend_runtime_configs USING btree (scope, config_key);


--
-- TOC entry 3721 (class 1259 OID 17662)
-- Name: uniq_onboarding_progress_user_role; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_onboarding_progress_user_role ON public.onboarding_progress USING btree (user_id, role);


--
-- TOC entry 3726 (class 1259 OID 17703)
-- Name: uniq_onboarding_steps_progress_kind; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_onboarding_steps_progress_kind ON public.onboarding_steps USING btree (progress_id, kind);


--
-- TOC entry 3731 (class 1259 OID 17739)
-- Name: uniq_payment_provider_account_links_account_provider; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_payment_provider_account_links_account_provider ON public.payment_provider_account_links USING btree (account_kind, account_id, provider);


--
-- TOC entry 3732 (class 1259 OID 17737)
-- Name: uniq_payment_provider_account_links_provider_customer_ref; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_payment_provider_account_links_provider_customer_ref ON public.payment_provider_account_links USING btree (provider, provider_customer_ref);


--
-- TOC entry 3753 (class 1259 OID 17897)
-- Name: uniq_recurring_expense_reminders_template_due; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_recurring_expense_reminders_template_due ON public.recurring_expense_reminders USING btree (template_id, due_date);


--
-- TOC entry 3647 (class 1259 OID 17912)
-- Name: uniq_subcategories_global_category_name; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_subcategories_global_category_name ON public.subcategories USING btree (category_id, name) WHERE (tenant_id IS NULL);


--
-- TOC entry 3648 (class 1259 OID 17911)
-- Name: uniq_subcategories_tenant_category_name; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_subcategories_tenant_category_name ON public.subcategories USING btree (tenant_id, category_id, name) WHERE (tenant_id IS NOT NULL);


--
-- TOC entry 3595 (class 1259 OID 16630)
-- Name: uniq_tenant_memberships_active_owner; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_tenant_memberships_active_owner ON public.tenant_memberships USING btree (tenant_id) WHERE ((role = 'owner'::public.membership_role) AND (status = 'active'::public.membership_status));


--
-- TOC entry 3596 (class 1259 OID 17162)
-- Name: uniq_tenant_memberships_tenant_user; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_tenant_memberships_tenant_user ON public.tenant_memberships USING btree (tenant_id, user_id);


--
-- TOC entry 3710 (class 1259 OID 17575)
-- Name: uniq_tenant_settings_tenant; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_tenant_settings_tenant ON public.tenant_settings USING btree (tenant_id);


--
-- TOC entry 3672 (class 1259 OID 17753)
-- Name: uniq_user_expense_settings_tenant_subject; Type: INDEX; Schema: public; Owner: app_user
--

CREATE UNIQUE INDEX uniq_user_expense_settings_tenant_subject ON public.user_expense_settings USING btree (tenant_id, subject_ref) WHERE (subject_ref IS NOT NULL);


--
-- TOC entry 3808 (class 2620 OID 17339)
-- Name: admin_sessions admin_sessions_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER admin_sessions_updated_at BEFORE UPDATE ON public.admin_sessions FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3807 (class 2620 OID 17317)
-- Name: admins admins_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER admins_updated_at BEFORE UPDATE ON public.admins FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3827 (class 2620 OID 17330)
-- Name: article_aliases article_aliases_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER article_aliases_updated_at BEFORE UPDATE ON public.article_aliases FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3826 (class 2620 OID 17337)
-- Name: articles articles_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER articles_updated_at BEFORE UPDATE ON public.articles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3831 (class 2620 OID 17323)
-- Name: audit_logs audit_logs_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER audit_logs_updated_at BEFORE UPDATE ON public.audit_logs FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3833 (class 2620 OID 17332)
-- Name: backlog backlog_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER backlog_updated_at BEFORE UPDATE ON public.backlog FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3824 (class 2620 OID 17319)
-- Name: categories categories_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER categories_updated_at BEFORE UPDATE ON public.categories FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3813 (class 2620 OID 17327)
-- Name: cities cities_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER cities_updated_at BEFORE UPDATE ON public.cities FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3812 (class 2620 OID 17328)
-- Name: countries countries_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER countries_updated_at BEFORE UPDATE ON public.countries FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3822 (class 2620 OID 17333)
-- Name: email_verification_tokens email_verification_tokens_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER email_verification_tokens_updated_at BEFORE UPDATE ON public.email_verification_tokens FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3820 (class 2620 OID 17340)
-- Name: expense_categories expense_categories_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER expense_categories_updated_at BEFORE UPDATE ON public.expense_categories FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3828 (class 2620 OID 17329)
-- Name: expense_items expense_items_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER expense_items_updated_at BEFORE UPDATE ON public.expense_items FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3821 (class 2620 OID 17314)
-- Name: expenses expenses_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER expenses_updated_at BEFORE UPDATE ON public.expenses FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3829 (class 2620 OID 17335)
-- Name: login_events login_events_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER login_events_updated_at BEFORE UPDATE ON public.login_events FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3823 (class 2620 OID 17325)
-- Name: manufacturers manufacturers_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER manufacturers_updated_at BEFORE UPDATE ON public.manufacturers FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3832 (class 2620 OID 17331)
-- Name: password_reset_tokens password_reset_tokens_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER password_reset_tokens_updated_at BEFORE UPDATE ON public.password_reset_tokens FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3818 (class 2620 OID 17313)
-- Name: payers payers_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER payers_updated_at BEFORE UPDATE ON public.payers FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3834 (class 2620 OID 17341)
-- Name: raw_labels raw_labels_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER raw_labels_updated_at BEFORE UPDATE ON public.raw_labels FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3819 (class 2620 OID 17336)
-- Name: receipts receipts_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER receipts_updated_at BEFORE UPDATE ON public.receipts FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3815 (class 2620 OID 17326)
-- Name: store_aliases store_aliases_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER store_aliases_updated_at BEFORE UPDATE ON public.store_aliases FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3814 (class 2620 OID 17324)
-- Name: stores stores_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER stores_updated_at BEFORE UPDATE ON public.stores FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3825 (class 2620 OID 17334)
-- Name: subcategories subcategories_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER subcategories_updated_at BEFORE UPDATE ON public.subcategories FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3817 (class 2620 OID 17342)
-- Name: supplier_aliases supplier_aliases_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER supplier_aliases_updated_at BEFORE UPDATE ON public.supplier_aliases FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3811 (class 2620 OID 17338)
-- Name: suppliers suppliers_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER suppliers_updated_at BEFORE UPDATE ON public.suppliers FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3816 (class 2620 OID 17316)
-- Name: tenant_memberships tenant_memberships_require_active_owner; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE CONSTRAINT TRIGGER tenant_memberships_require_active_owner AFTER INSERT OR DELETE OR UPDATE OF tenant_id, role, status ON public.tenant_memberships DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.enforce_tenant_single_active_owner();


--
-- TOC entry 3810 (class 2620 OID 17321)
-- Name: tenants tenants_require_active_owner; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE CONSTRAINT TRIGGER tenants_require_active_owner AFTER INSERT ON public.tenants DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.enforce_tenant_single_active_owner();


--
-- TOC entry 3830 (class 2620 OID 17318)
-- Name: user_expense_settings user_expense_settings_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER user_expense_settings_updated_at BEFORE UPDATE ON public.user_expense_settings FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3809 (class 2620 OID 17322)
-- Name: users users_updated_at; Type: TRIGGER; Schema: public; Owner: app_user
--

CREATE TRIGGER users_updated_at BEFORE UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- TOC entry 3793 (class 2606 OID 17498)
-- Name: admin_invitations admin_invitations_invited_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.admin_invitations
    ADD CONSTRAINT admin_invitations_invited_by_fkey FOREIGN KEY (invited_by) REFERENCES public.admins(id) ON DELETE CASCADE;


--
-- TOC entry 3754 (class 2606 OID 16436)
-- Name: admin_sessions admin_sessions_admin_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.admin_sessions
    ADD CONSTRAINT admin_sessions_admin_id_fkey FOREIGN KEY (admin_id) REFERENCES public.admins(id) ON DELETE CASCADE;


--
-- TOC entry 3785 (class 2606 OID 16944)
-- Name: article_aliases article_aliases_article_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.article_aliases
    ADD CONSTRAINT article_aliases_article_id_fkey FOREIGN KEY (article_id) REFERENCES public.articles(id) ON DELETE SET NULL;


--
-- TOC entry 3784 (class 2606 OID 16939)
-- Name: article_aliases article_aliases_supplier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.article_aliases
    ADD CONSTRAINT article_aliases_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE CASCADE;


--
-- TOC entry 3783 (class 2606 OID 16918)
-- Name: articles articles_manufacturer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT articles_manufacturer_id_fkey FOREIGN KEY (manufacturer_id) REFERENCES public.manufacturers(id) ON DELETE SET NULL;


--
-- TOC entry 3782 (class 2606 OID 16913)
-- Name: articles articles_subcategory_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT articles_subcategory_id_fkey FOREIGN KEY (subcategory_id) REFERENCES public.subcategories(id) ON DELETE SET NULL;


--
-- TOC entry 3757 (class 2606 OID 16551)
-- Name: cities cities_country_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.cities
    ADD CONSTRAINT cities_country_fkey FOREIGN KEY (country) REFERENCES public.countries(country) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- TOC entry 3790 (class 2606 OID 16988)
-- Name: dedup_ignored_clusters dedup_ignored_clusters_admin_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.dedup_ignored_clusters
    ADD CONSTRAINT dedup_ignored_clusters_admin_id_fkey FOREIGN KEY (admin_id) REFERENCES public.admins(id) ON DELETE CASCADE;


--
-- TOC entry 3779 (class 2606 OID 16862)
-- Name: email_verification_tokens email_verification_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- TOC entry 3771 (class 2606 OID 16800)
-- Name: expense_categories expense_categories_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_categories
    ADD CONSTRAINT expense_categories_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- TOC entry 3797 (class 2606 OID 17766)
-- Name: expense_contexts expense_contexts_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_contexts
    ADD CONSTRAINT expense_contexts_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- TOC entry 3789 (class 2606 OID 17706)
-- Name: expense_items expense_items_alias_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_items
    ADD CONSTRAINT expense_items_alias_id_fkey FOREIGN KEY (alias_id) REFERENCES public.article_aliases(id) ON DELETE SET NULL;


--
-- TOC entry 3786 (class 2606 OID 16961)
-- Name: expense_items expense_items_article_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_items
    ADD CONSTRAINT expense_items_article_id_fkey FOREIGN KEY (article_id) REFERENCES public.articles(id) ON DELETE SET NULL;


--
-- TOC entry 3787 (class 2606 OID 16966)
-- Name: expense_items expense_items_expense_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_items
    ADD CONSTRAINT expense_items_expense_id_fkey FOREIGN KEY (expense_id) REFERENCES public.expenses(id) ON DELETE CASCADE;


--
-- TOC entry 3788 (class 2606 OID 16971)
-- Name: expense_items expense_items_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_items
    ADD CONSTRAINT expense_items_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- TOC entry 3803 (class 2606 OID 17855)
-- Name: expense_template_lines expense_template_lines_article_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_template_lines
    ADD CONSTRAINT expense_template_lines_article_id_fkey FOREIGN KEY (article_id) REFERENCES public.articles(id) ON DELETE SET NULL;


--
-- TOC entry 3804 (class 2606 OID 17860)
-- Name: expense_template_lines expense_template_lines_template_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_template_lines
    ADD CONSTRAINT expense_template_lines_template_id_fkey FOREIGN KEY (template_id) REFERENCES public.expense_templates(id) ON DELETE CASCADE;


--
-- TOC entry 3798 (class 2606 OID 17812)
-- Name: expense_templates expense_templates_default_expense_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_templates
    ADD CONSTRAINT expense_templates_default_expense_category_id_fkey FOREIGN KEY (default_expense_category_id) REFERENCES public.expense_categories(id) ON DELETE SET NULL;


--
-- TOC entry 3799 (class 2606 OID 17817)
-- Name: expense_templates expense_templates_default_expense_context_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_templates
    ADD CONSTRAINT expense_templates_default_expense_context_id_fkey FOREIGN KEY (default_expense_context_id) REFERENCES public.expense_contexts(id) ON DELETE SET NULL;


--
-- TOC entry 3801 (class 2606 OID 17827)
-- Name: expense_templates expense_templates_default_payer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_templates
    ADD CONSTRAINT expense_templates_default_payer_id_fkey FOREIGN KEY (default_payer_id) REFERENCES public.payers(id) ON DELETE SET NULL;


--
-- TOC entry 3800 (class 2606 OID 17822)
-- Name: expense_templates expense_templates_default_supplier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_templates
    ADD CONSTRAINT expense_templates_default_supplier_id_fkey FOREIGN KEY (default_supplier_id) REFERENCES public.suppliers(id) ON DELETE SET NULL;


--
-- TOC entry 3802 (class 2606 OID 17832)
-- Name: expense_templates expense_templates_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expense_templates
    ADD CONSTRAINT expense_templates_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- TOC entry 3773 (class 2606 OID 16822)
-- Name: expenses expenses_expense_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_expense_category_id_fkey FOREIGN KEY (expense_category_id) REFERENCES public.expense_categories(id) ON DELETE SET NULL;


--
-- TOC entry 3778 (class 2606 OID 17840)
-- Name: expenses expenses_expense_context_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_expense_context_id_fkey FOREIGN KEY (expense_context_id) REFERENCES public.expense_contexts(id) ON DELETE SET NULL;


--
-- TOC entry 3774 (class 2606 OID 16837)
-- Name: expenses expenses_payer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_payer_id_fkey FOREIGN KEY (payer_id) REFERENCES public.payers(id);


--
-- TOC entry 3775 (class 2606 OID 16842)
-- Name: expenses expenses_receipt_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_receipt_id_fkey FOREIGN KEY (receipt_id) REFERENCES public.receipts(id) ON DELETE SET NULL;


--
-- TOC entry 3772 (class 2606 OID 16817)
-- Name: expenses expenses_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id) ON DELETE SET NULL;


--
-- TOC entry 3777 (class 2606 OID 17504)
-- Name: expenses expenses_supplier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE SET NULL;


--
-- TOC entry 3776 (class 2606 OID 16847)
-- Name: expenses expenses_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- TOC entry 3795 (class 2606 OID 17657)
-- Name: onboarding_progress onboarding_progress_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.onboarding_progress
    ADD CONSTRAINT onboarding_progress_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- TOC entry 3796 (class 2606 OID 17697)
-- Name: onboarding_steps onboarding_steps_progress_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.onboarding_steps
    ADD CONSTRAINT onboarding_steps_progress_id_fkey FOREIGN KEY (progress_id) REFERENCES public.onboarding_progress(id) ON DELETE CASCADE;


--
-- TOC entry 3765 (class 2606 OID 16695)
-- Name: payers payers_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.payers
    ADD CONSTRAINT payers_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- TOC entry 3770 (class 2606 OID 17512)
-- Name: receipts receipts_expense_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.receipts
    ADD CONSTRAINT receipts_expense_category_id_fkey FOREIGN KEY (expense_category_id) REFERENCES public.expense_categories(id) ON DELETE SET NULL;


--
-- TOC entry 3767 (class 2606 OID 16727)
-- Name: receipts receipts_payer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.receipts
    ADD CONSTRAINT receipts_payer_id_fkey FOREIGN KEY (payer_id) REFERENCES public.payers(id) ON DELETE SET NULL;


--
-- TOC entry 3766 (class 2606 OID 16722)
-- Name: receipts receipts_store_alias_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.receipts
    ADD CONSTRAINT receipts_store_alias_id_fkey FOREIGN KEY (store_alias_id) REFERENCES public.store_aliases(id) ON DELETE SET NULL;


--
-- TOC entry 3768 (class 2606 OID 16732)
-- Name: receipts receipts_supplier_alias_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.receipts
    ADD CONSTRAINT receipts_supplier_alias_id_fkey FOREIGN KEY (supplier_alias_id) REFERENCES public.supplier_aliases(id) ON DELETE SET NULL;


--
-- TOC entry 3769 (class 2606 OID 16737)
-- Name: receipts receipts_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.receipts
    ADD CONSTRAINT receipts_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- TOC entry 3805 (class 2606 OID 17887)
-- Name: recurring_expense_reminders recurring_expense_reminders_recorded_expense_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.recurring_expense_reminders
    ADD CONSTRAINT recurring_expense_reminders_recorded_expense_id_fkey FOREIGN KEY (recorded_expense_id) REFERENCES public.expenses(id) ON DELETE SET NULL;


--
-- TOC entry 3806 (class 2606 OID 17892)
-- Name: recurring_expense_reminders recurring_expense_reminders_template_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.recurring_expense_reminders
    ADD CONSTRAINT recurring_expense_reminders_template_id_fkey FOREIGN KEY (template_id) REFERENCES public.expense_templates(id) ON DELETE CASCADE;


--
-- TOC entry 3760 (class 2606 OID 16587)
-- Name: store_aliases store_aliases_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.store_aliases
    ADD CONSTRAINT store_aliases_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id) ON DELETE SET NULL;


--
-- TOC entry 3759 (class 2606 OID 16571)
-- Name: stores stores_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.stores
    ADD CONSTRAINT stores_city_id_fkey FOREIGN KEY (city_id) REFERENCES public.cities(id) ON DELETE SET NULL;


--
-- TOC entry 3758 (class 2606 OID 16566)
-- Name: stores stores_supplier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.stores
    ADD CONSTRAINT stores_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE CASCADE;


--
-- TOC entry 3780 (class 2606 OID 16898)
-- Name: subcategories subcategories_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.subcategories
    ADD CONSTRAINT subcategories_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id) ON DELETE CASCADE;


--
-- TOC entry 3781 (class 2606 OID 17905)
-- Name: subcategories subcategories_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.subcategories
    ADD CONSTRAINT subcategories_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- TOC entry 3764 (class 2606 OID 16674)
-- Name: supplier_aliases supplier_aliases_supplier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.supplier_aliases
    ADD CONSTRAINT supplier_aliases_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE SET NULL;


--
-- TOC entry 3755 (class 2606 OID 16512)
-- Name: tenant_invitations tenant_invitations_invited_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.tenant_invitations
    ADD CONSTRAINT tenant_invitations_invited_by_fkey FOREIGN KEY (invited_by) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- TOC entry 3756 (class 2606 OID 16517)
-- Name: tenant_invitations tenant_invitations_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.tenant_invitations
    ADD CONSTRAINT tenant_invitations_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- TOC entry 3763 (class 2606 OID 16625)
-- Name: tenant_memberships tenant_memberships_invited_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.tenant_memberships
    ADD CONSTRAINT tenant_memberships_invited_by_fkey FOREIGN KEY (invited_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- TOC entry 3761 (class 2606 OID 16615)
-- Name: tenant_memberships tenant_memberships_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.tenant_memberships
    ADD CONSTRAINT tenant_memberships_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- TOC entry 3762 (class 2606 OID 16620)
-- Name: tenant_memberships tenant_memberships_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.tenant_memberships
    ADD CONSTRAINT tenant_memberships_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- TOC entry 3794 (class 2606 OID 17562)
-- Name: tenant_settings tenant_settings_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.tenant_settings
    ADD CONSTRAINT tenant_settings_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- TOC entry 3791 (class 2606 OID 17035)
-- Name: user_expense_settings user_expense_settings_default_payer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.user_expense_settings
    ADD CONSTRAINT user_expense_settings_default_payer_id_fkey FOREIGN KEY (default_payer_id) REFERENCES public.payers(id) ON DELETE SET NULL;


--
-- TOC entry 3792 (class 2606 OID 17040)
-- Name: user_expense_settings user_expense_settings_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user
--

ALTER TABLE ONLY public.user_expense_settings
    ADD CONSTRAINT user_expense_settings_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


-- Completed on 2026-09-26 10:03:01 CEST

--
-- PostgreSQL database dump complete
--


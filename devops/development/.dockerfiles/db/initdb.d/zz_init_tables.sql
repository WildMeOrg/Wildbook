--
-- PostgreSQL database dump
--

-- Dumped from database version 13.4 (Debian 13.4-4.pgdg110+1)
-- Dumped by pg_dump version 13.4 (Debian 13.4-4.pgdg110+1)

\c wildbook

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

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: ASSETSTORE; Type: TABLE; Schema: public; Owner: wildbook
--

CREATE TABLE public."ASSETSTORE" (
    "ID" integer NOT NULL,
    "CONFIG" bytea,
    "NAME" character varying(255),
    "USAGE" character varying(255),
    "WRITABLE" boolean NOT NULL,
    "TYPE" character varying(255) NOT NULL
);


ALTER TABLE public."ASSETSTORE" OWNER TO wildbook;

--
-- Name: ASSETSTORE_ID_seq; Type: SEQUENCE; Schema: public; Owner: wildbook
--

CREATE SEQUENCE public."ASSETSTORE_ID_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public."ASSETSTORE_ID_seq" OWNER TO wildbook;

--
-- Name: ASSETSTORE_ID_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: wildbook
--

ALTER SEQUENCE public."ASSETSTORE_ID_seq" OWNED BY public."ASSETSTORE"."ID";


--
-- Name: SYSTEMVALUE; Type: TABLE; Schema: public; Owner: wildbook
--

CREATE TABLE public."SYSTEMVALUE" (
    "KEY" character varying(255) NOT NULL,
    "VALUE" text,
    "VERSION" bigint
);


ALTER TABLE public."SYSTEMVALUE" OWNER TO wildbook;

--
-- Name: ASSETSTORE ID; Type: DEFAULT; Schema: public; Owner: wildbook
--

ALTER TABLE ONLY public."ASSETSTORE" ALTER COLUMN "ID" SET DEFAULT nextval('public."ASSETSTORE_ID_seq"'::regclass);


--
-- Data for Name: ASSETSTORE; Type: TABLE DATA; Schema: public; Owner: wildbook
--

COPY public."ASSETSTORE" ("ID", "CONFIG", "NAME", "USAGE", "WRITABLE", "TYPE") FROM stdin;
1	\\xaced0005737200226f72672e65636f6365616e2e6d656469612e417373657453746f7265436f6e6669673020361c20a7e0310200014c0006636f6e66696774000f4c6a6176612f7574696c2f4d61703b7870737200116a6176612e7574696c2e486173684d61700507dac1c31660d103000246000a6c6f6164466163746f724900097468726573686f6c6478703f4000000000000c77080000001000000002740004726f6f7474002b2f7573722f6c6f63616c2f746f6d6361742f776562617070732f77696c64626f6f6b5f646174615f646972740007776562726f6f74740025687474703a2f2f6c6f63616c686f73743a38312f77696c64626f6f6b5f646174615f64697278	Default Local AssetStore	default	t	LOCAL
\.

-- this one sets http://wildbook:8080/
-- 1	\\xaced0005737200226f72672e65636f6365616e2e6d656469612e417373657453746f7265436f6e6669673020361c20a7e0310200014c0006636f6e66696774000f4c6a6176612f7574696c2f4d61703b7870737200116a6176612e7574696c2e486173684d61700507dac1c31660d103000246000a6c6f6164466163746f724900097468726573686f6c6478703f4000000000000c77080000001000000002740004726f6f7474002b2f7573722f6c6f63616c2f746f6d6361742f776562617070732f77696c64626f6f6b5f646174615f646972740007776562726f6f74740026687474703a2f2f77696c64626f6f6b3a383038302f77696c64626f6f6b5f646174615f64697278	Default Local AssetStore	default	t	LOCAL

--
-- Data for Name: SYSTEMVALUE; Type: TABLE DATA; Schema: public; Owner: wildbook
--

COPY public."SYSTEMVALUE" ("KEY", "VALUE", "VERSION") FROM stdin;
SERVER_INFO	{"type":"JSONObject","value":{"scheme":"http","contextPath":"/","context":"context0","serverName":"wildbook","serverPort":8080,"timestamp":1711667831907}}	1711667831940
\.


--
-- Name: ASSETSTORE_ID_seq; Type: SEQUENCE SET; Schema: public; Owner: wildbook
--

SELECT pg_catalog.setval('public."ASSETSTORE_ID_seq"', 2, true);


--
-- Name: ASSETSTORE ASSETSTORE_pkey; Type: CONSTRAINT; Schema: public; Owner: wildbook
--

ALTER TABLE ONLY public."ASSETSTORE"
    ADD CONSTRAINT "ASSETSTORE_pkey" PRIMARY KEY ("ID");


--
-- Name: SYSTEMVALUE SYSTEMVALUE_pkey; Type: CONSTRAINT; Schema: public; Owner: wildbook
--

ALTER TABLE ONLY public."SYSTEMVALUE"
    ADD CONSTRAINT "SYSTEMVALUE_pkey" PRIMARY KEY ("KEY");


--
-- PostgreSQL database dump complete
--


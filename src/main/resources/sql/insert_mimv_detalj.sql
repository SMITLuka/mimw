-- ============================================================
-- T-SQL SELECT for INSERT INTO MIMV_DETALJ (Pantheon)
-- Executed directly via JdbcTemplate against Pantheon MSSQL.
-- All AS400 tables are accessed via 4-part linked server naming:
--   [linkedServer].[catalog].[library].[table]
--
-- HF tables (hfs, hfk, hfb, etc.) use <HFLIBRARY> (e.g. KB0D1K).
-- PERV uses <MAINLIBRARY> (e.g. KB0D1).
-- MAR, PCD use <PLIBRARY> (e.g. KB0DP).
-- KLCHCPP (brand lookup) uses hardcoded library IVASXT.
-- MIMV_ODABRANI_TIPOVI_OBVEZNIKA is a local Pantheon MSSQL table — no linked server prefix.
--
-- All AS400 column names are uppercase throughout — MSDASQL returns column metadata in
-- uppercase and Pantheon SQL Server uses a case-sensitive collation.
-- ============================================================
-- Placeholders replaced at runtime:
--   <LINKEDSERVER>  AS400 linked server name   (e.g. RENAULT)
--   <CATALOG>       AS400 catalog name          (e.g. ADRIAVC1)
--   <HFLIBRARY>     AS400 HF tables library     (e.g. KB0D1K)
--   <MAINLIBRARY>   AS400 main data library     (e.g. KB0D1)   -- PERV
--   <PLIBRARY>      AS400 P library             (e.g. KB0DP)   -- MAR, PCD
--   <SIFPOD>        mandatorId                  (e.g. 0000B0)
--   <OIB>           OIB obveznika               (e.g. 30985203273)
--   <DATUM>         DatumPP as YYYYMMDD int      (e.g. 20260401)
--   <SIFOBR>        SifraObrascaPP              (e.g. 401 or 405)
--   <RBR>           RedniBrojPP                 (e.g. 1)
--   <RBRPROM>       RedniBrojPPPromjena         (e.g. 0)
--   <ODDATUMA>      Period start YYYYMMDD       (dateFrom)
--   <DODATUMA>      Period end   YYYYMMDD       (dateTo)
--   <BRANCH>        Company branch number       (companyId)
-- ============================================================
-- Column order matches MIMV_DETALJ:
--   OIB_OBVEZNIKA, DATUM_PP, SIFRA_OBRASCA_PP, REDNI_BROJ_PP, REDNI_BROJ_PP_PROM,
--   SIFRA_VOZILA, STATUS_VOZILA, VRSTA_VOZILA, MARKA_VOZILA, TIP_VARIJANTA_TRG_NAZIV,
--   VIN_OZNAKA, VRSTA_GORIVA, DATUM_PRVE_REGISTRACIJE, PROSJ_EMISIJA_CO2, RAZINA_EMISIJE,
--   RADNI_OBUJAM_MOTORA, SNAGA_MOTORA, PRODAJNA_CIJENA, BROJ_PRIJEDJENIH_KM,
--   KAMPER, PLUG_IN, VOZILO_71, VOZILO_81, TESTNO_VOZILO, DEPRECIJACIJA,
--   POREZNI_OBVEZNIK, OIB, BROJ_RACUNA, DATUM_IZDAVANJA_RACUNA, OBRACUNATI_IZNOS_PP
-- ============================================================

WITH vehs AS (
    SELECT
        hfs.HFSKEY                                                                                  AS HFSKEY,
        hfs.HFSFZGART                                                                               AS HFSFZGART,
        '1'                                                                                         AS vrsta_vozila,
        klc.CHYSAA                                                                                  AS CHYSAA,
        hfp.HFPTXT                                                                                  AS naziv,
        TRIM(CASE WHEN UPPER(hfp.HFPTXT) LIKE '%AUTOM%' THEN 'automatski' ELSE 'rucni' END)
            + ', ' +
        TRIM(CASE WHEN pcd.PCDLACKART = 'M' THEN 'metalik' ELSE 'obicna' END)                      AS dodatak,
        50 - LEN(
            TRIM(CASE WHEN UPPER(hfp.HFPTXT) LIKE '%AUTOM%' THEN 'automatski' ELSE 'rucni' END)
            + ', ' +
            TRIM(CASE WHEN pcd.PCDLACKART = 'M' THEN 'metalik' ELSE 'obicna' END)
        ) - 2                                                                                       AS nazlen,
        hfs.HFSFGST                                                                                 AS HFSFGST,
        hfs.HFSTRB                                                                                  AS HFSTRB,
        hfs.HFSCO2                                                                                  AS HFSCO2,
        hfs.HFSHUBR                                                                                 AS HFSHUBR,
        hfs.HFSKW                                                                                   AS HFSKW,
        hfs.HFSZULDAT                                                                               AS HFSZULDAT,
        hfs.HFSKM                                                                                   AS HFSKM,
        hfs.HFSMAR                                                                                  AS HFSMAR,
        CASE WHEN t1.HFBDATFTR >= 20170101
             THEN ''
             ELSE CASE WHEN UPPER(hfs.HFSTRB) = 'N' THEN 'VI' ELSE '' END
        END                                                                                         AS razina_emisije,
        hfk.HFBRPNM1                                                                               AS HFBRPNM1,
        hfk.HFKKFNM1                                                                               AS HFKKFNM1,
        CASE WHEN hfk.HFBRPNM1 = '' THEN hfk.HFKKFUIDNR ELSE hfk.HFBRPUIDNR END                   AS oib_poreznog,
        t1.HFBBLFA                                                                                  AS HFBBLFA,
        t1.AENBET                                                                                   AS AENBET,
        t1.ANLBET                                                                                   AS ANLBET,
        t1.HFBDATFTR                                                                                AS HFBDATFTR,
        CASE WHEN t2.HBJHRTBAS IS NULL THEN hfk.HFKHRTBAS ELSE t2.HBJHRTBAS END                   AS prod_cijena,
        CASE WHEN t2.HBJHRTVAL IS NULL THEN hfk.HFKHRTVAL ELSE t2.HBJHRTVAL END                   AS obracunati_pp,
        perv.PESNAME                                                                                AS PESNAME
    FROM
        [<LINKEDSERVER>].[<CATALOG>].[<HFLIBRARY>].[HFS]             AS hfs
        LEFT JOIN [<LINKEDSERVER>].[<CATALOG>].[<HFLIBRARY>].[HFK]   AS hfk  ON hfs.HFSKEY = hfk.HFKKEY
        LEFT JOIN [<LINKEDSERVER>].[<CATALOG>].[<MAINLIBRARY>].[PERV] AS perv ON hfk.HFKVK = perv.PEBPER
        JOIN      [<LINKEDSERVER>].[<CATALOG>].[<HFLIBRARY>].[HFB]   AS t1   ON hfs.HFSKEY = t1.HFBKEY
        LEFT JOIN [<LINKEDSERVER>].[<CATALOG>].[<HFLIBRARY>].[HBJ]   AS t2   ON
            t1.HFBKEY = t2.HBJHFSKEY1
            AND t1.HFBBLFA = t2.HBJBNR
            AND t2.HBJBDAT = t1.HFBDATFTR
            AND t2.HBJAKT IN ('FA', 'FG')
        LEFT JOIN [<LINKEDSERVER>].[<CATALOG>].[<HFLIBRARY>].[HFP]   AS hfp  ON hfs.HFSKEY = hfp.HFPHFSNR
        JOIN      [<LINKEDSERVER>].[<CATALOG>].[<PLIBRARY>].[MAR]    AS mar  ON mar.MARMAR = hfs.HFSMAR
        LEFT JOIN [<LINKEDSERVER>].[<CATALOG>].[<PLIBRARY>].[PCD]    AS pcd  ON hfs.HFSFAR = pcd.PCDPCD AND hfs.HFSMAR = pcd.PCDMAR
        LEFT JOIN [<LINKEDSERVER>].[<CATALOG>].[IVASXT].[KLCHCPP]    AS klc  ON UPPER(mar.MARTXT) = klc.CHL6AQ
    WHERE
        t1.HFBDATFTR BETWEEN <ODDATUMA> AND <DODATUMA>
        AND (
            CASE WHEN hfk.HFKDATZOLL <> 0 THEN hfk.HFKDATZOLL END BETWEEN <ODDATUMA> AND <DODATUMA>
            OR hfk.HFKDATZOLL = 0
            OR hfk.HFKDATZOLL >= <DODATUMA>
        )
        AND hfs.HFSVKSTS = 'F'
        AND hfp.HFPPART = 'F'
        AND hfk.HFKHRTKZ <> ''
        AND (hfk.HFKHRTVAL <> 0 OR (t2.HBJHRTVAL IS NOT NULL AND t2.HBJHRTVAL <> 0))
        AND CASE WHEN t2.HBJHRTBAS IS NULL THEN hfk.HFKHRTBAS ELSE t2.HBJHRTBAS END > 0
        AND (
            (hfs.HFSFZGART IN ('N', 'V') AND EXISTS (SELECT 1 FROM MIMV_ODABRANI_TIPOVI_OBVEZNIKA WHERE MV02_TRGOVAC_NOVIM = 1))
            OR
            (hfs.HFSFZGART IN ('A', 'G') AND EXISTS (SELECT 1 FROM MIMV_ODABRANI_TIPOVI_OBVEZNIKA WHERE MV03_TRGOVAC_RABLJENIM = 1))
        )
        AND CASE WHEN t1.AENBET > 0 THEN t1.AENBET ELSE t1.ANLBET END = <BRANCH>
)
SELECT
    '<OIB>'                                                                                         AS OIB_OBVEZNIKA,
    <DATUM>                                                                                         AS DATUM_PP,
    '<SIFOBR>'                                                                                      AS SIFRA_OBRASCA_PP,
    <RBR>                                                                                           AS REDNI_BROJ_PP,
    <RBRPROM>                                                                                       AS REDNI_BROJ_PP_PROM,
    HFSKEY                                                                                          AS SIFRA_VOZILA,
    CASE WHEN HFSFZGART IN ('A', 'G') THEN 'R'
         WHEN HFSFZGART = 'N'         THEN 'N'
         WHEN HFSFZGART = 'V'         THEN 'NT'
         ELSE HFSFZGART
    END                                                                                             AS STATUS_VOZILA,
    vrsta_vozila                                                                                    AS VRSTA_VOZILA,
    ISNULL(CHYSAA, '')                                                                              AS MARKA_VOZILA,
    LEFT(TRIM(LEFT(naziv, CASE WHEN nazlen > 0 THEN nazlen ELSE 0 END)) + ', ' + dodatak, 50)       AS TIP_VARIJANTA_TRG_NAZIV,
    TRIM(SUBSTRING(HFSFGST, 4, 17))                                                                 AS VIN_OZNAKA,
    CASE WHEN UPPER(HFSTRB) = 'N' THEN 'D' ELSE HFSTRB END                                         AS VRSTA_GORIVA,
    HFSZULDAT                                                                                       AS DATUM_PRVE_REGISTRACIJE,
    HFSCO2                                                                                          AS PROSJ_EMISIJA_CO2,
    razina_emisije                                                                                  AS RAZINA_EMISIJE,
    CASE WHEN '<SIFPOD>' = '0000F7' THEN HFSHUBR ELSE 0 END                                        AS RADNI_OBUJAM_MOTORA,
    HFSKW                                                                                           AS SNAGA_MOTORA,
    prod_cijena                                                                                     AS PRODAJNA_CIJENA,
    HFSKM                                                                                           AS BROJ_PRIJEDJENIH_KM,
    ''                                                                                              AS KAMPER,
    0                                                                                               AS PLUG_IN,
    ''                                                                                              AS VOZILO_71,
    ''                                                                                              AS VOZILO_81,
    CAST(0 AS DECIMAL(7,2))                                                                         AS TESTNO_VOZILO,
    CAST(0 AS DECIMAL(7,2))                                                                         AS DEPRECIJACIJA,
    LEFT(CASE WHEN HFBRPNM1 = '' THEN HFKKFNM1 ELSE HFBRPNM1 END, 80)                              AS POREZNI_OBVEZNIK,
    CASE WHEN oib_poreznog IS NOT NULL THEN LEFT(oib_poreznog, 11) ELSE '' END                      AS OIB,
    LEFT(TRIM(CAST(HFBBLFA AS VARCHAR(20))) + '/' + TRIM(CAST(CASE WHEN AENBET > 0 THEN AENBET ELSE ANLBET END AS VARCHAR(20))) + '/6', 30) AS BROJ_RACUNA,
    HFBDATFTR                                                                                       AS DATUM_IZDAVANJA_RACUNA,
    obracunati_pp                                                                                   AS OBRACUNATI_IZNOS_PP
FROM vehs
ORDER BY PESNAME, HFSMAR, HFBDATFTR

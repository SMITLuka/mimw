-- ============================================================
-- DB2 for i SELECT for INSERT INTO MIMV_DETALJ (Pantheon), direct AS400 variant.
-- Executed directly on AS400 through JT400 (naming=sql: LIBRARY.TABLE) when the company has AS400_HOST.
-- Same columns, joins and filters as insert_mimv_detalj.sql (linked server variant), in DB2 syntax:
--   no [linkedServer].[catalog] prefix, COALESCE instead of ISNULL, LENGTH instead of LEN, || instead of +.
-- MIMV_ODABRANI_TIPOVI_OBVEZNIKA is a SQL Server table, so its flags are resolved in Java and passed
-- as constant conditions <MV02> / <MV03> ("1 = 1" or "1 = 0").
-- ============================================================
-- Placeholders replaced at runtime:
--   <HFLIBRARY>     AS400 HF tables library     (e.g. KB0D1K)
--   <MAINLIBRARY>   AS400 main data library     (e.g. KB0D1)   -- PERV
--   <PLIBRARY>      AS400 P library             (e.g. KB0DP)   -- MAR, PCD
--   <SIFPOD>        mandatorId
--   <OIB>           OIB obveznika
--   <DATUM>         DatumPP as YYYYMMDD int
--   <SIFOBR>        SifraObrascaPP
--   <RBR>           RedniBrojPP
--   <RBRPROM>       RedniBrojPPPromjena
--   <ODDATUMA>      Period start YYYYMMDD
--   <DODATUMA>      Period end   YYYYMMDD
--   <BRANCHFILTER>  Branch condition from AS400_BRANCH, or empty (all branches of the data group)
--   <MV02>          1 = 1 when MV02 (trgovac novim) is selected, otherwise 1 = 0
--   <MV03>          1 = 1 when MV03 (trgovac rabljenim) is selected, otherwise 1 = 0
-- Column order matches MIMV_DETALJ (30 columns), same as insert_mimv_detalj.sql.
-- ============================================================

WITH vehs AS (
    SELECT
        hfs.HFSKEY                                                                                  AS HFSKEY,
        hfs.HFSFZGART                                                                               AS HFSFZGART,
        '1'                                                                                         AS vrsta_vozila,
        klc.CHYSAA                                                                                  AS CHYSAA,
        hfp.HFPTXT                                                                                  AS naziv,
        TRIM(CASE WHEN UPPER(hfp.HFPTXT) LIKE '%AUTOM%' THEN 'automatski' ELSE 'rucni' END)
            || ', ' ||
        TRIM(CASE WHEN pcd.PCDLACKART = 'M' THEN 'metalik' ELSE 'obicna' END)                      AS dodatak,
        50 - LENGTH(
            TRIM(CASE WHEN UPPER(hfp.HFPTXT) LIKE '%AUTOM%' THEN 'automatski' ELSE 'rucni' END)
            || ', ' ||
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
        t1.HFBRPNM1                                                                                AS HFBRPNM1,
        hfk.HFKKFNM1                                                                               AS HFKKFNM1,
        CASE WHEN t1.HFBRPNM1 = '' THEN hfk.HFKKFUIDNR ELSE t1.HFBRPUIDNR END                     AS oib_poreznog,
        t1.HFBBLFA                                                                                  AS HFBBLFA,
        t1.AENBET                                                                                   AS AENBET,
        t1.ANLBET                                                                                   AS ANLBET,
        t1.HFBDATFTR                                                                                AS HFBDATFTR,
        COALESCE(t2.HBJHRTBAS, hfk.HFKHRTBAS)                                                       AS prod_cijena,
        COALESCE(t2.HBJHRTVAL, hfk.HFKHRTVAL)                                                       AS obracunati_pp,
        perv.PESNAME                                                                                AS PESNAME
    FROM
        <HFLIBRARY>.HFS             hfs
        LEFT JOIN <HFLIBRARY>.HFK   hfk  ON hfs.HFSKEY = hfk.HFKKEY
        LEFT JOIN <MAINLIBRARY>.PERV perv ON hfk.HFKVK = perv.PEBPER
        JOIN      <HFLIBRARY>.HFB   t1   ON hfs.HFSKEY = t1.HFBKEY
        LEFT JOIN <HFLIBRARY>.HBJ   t2   ON
            t1.HFBKEY = t2.HBJHFSKEY1
            AND t1.HFBBLFA = t2.HBJBNR
            AND t2.HBJBDAT = t1.HFBDATFTR
            AND t2.HBJAKT IN ('FA', 'FG')
        LEFT JOIN <HFLIBRARY>.HFP   hfp  ON hfs.HFSKEY = hfp.HFPHFSNR
        JOIN      <PLIBRARY>.MAR    mar  ON mar.MARMAR = hfs.HFSMAR
        LEFT JOIN <PLIBRARY>.PCD    pcd  ON hfs.HFSFAR = pcd.PCDPCD AND hfs.HFSMAR = pcd.PCDMAR
        LEFT JOIN IVASXT.KLCHCPP    klc  ON UPPER(mar.MARTXT) = klc.CHL6AQ
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
        AND COALESCE(t2.HBJHRTBAS, hfk.HFKHRTBAS) > 0
        AND (
            (hfs.HFSFZGART IN ('N', 'V') AND <MV02>)
            OR
            (hfs.HFSFZGART IN ('A', 'G') AND <MV03>)
        )
        <BRANCHFILTER>
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
    COALESCE(CHYSAA, '')                                                                            AS MARKA_VOZILA,
    LEFT(TRIM(LEFT(naziv, CASE WHEN nazlen > 0 THEN nazlen ELSE 0 END)) || ', ' || dodatak, 50)     AS TIP_VARIJANTA_TRG_NAZIV,
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
    CAST(TRIM(CASE WHEN TRIM(HFBRPNM1) = '' THEN HFKKFNM1 ELSE HFBRPNM1 END) AS VARCHAR(80))       AS POREZNI_OBVEZNIK,
    CASE WHEN oib_poreznog IS NOT NULL THEN LEFT(oib_poreznog, 11) ELSE '' END                      AS OIB,
    LEFT(TRIM(CAST(BIGINT(HFBBLFA) AS VARCHAR(20))) || '/'
         || TRIM(CAST(BIGINT(CASE WHEN AENBET > 0 THEN AENBET ELSE ANLBET END) AS VARCHAR(20))) || '/6', 30) AS BROJ_RACUNA,
    HFBDATFTR                                                                                       AS DATUM_IZDAVANJA_RACUNA,
    obracunati_pp                                                                                   AS OBRACUNATI_IZNOS_PP
FROM vehs
ORDER BY PESNAME, HFSMAR, HFBDATFTR

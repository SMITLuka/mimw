-- ============================================================
-- DB2 SELECT for INSERT INTO MIMV_DETALJ (Pantheon)
-- Executed via EXEC('...') AT [linkedServer] from T-SQL wrapper.
-- ============================================================
-- Placeholders replaced at runtime:
--   <SIFPOD>    mandatorId               (e.g. 0000B0)
--   <OIB>       OIB obveznika            (e.g. 30985203273)
--   <DATUM>     DatumPP as YYYYMMDD int  (e.g. 20260401)
--   <SIFOBR>    SifraObrascaPP           (e.g. 401 or 405)
--   <RBR>       RedniBrojPP              (e.g. 1)
--   <RBRPROM>   RedniBrojPPPromjena      (e.g. 0)
--   <ODDATUMA>  Period start YYYYMMDD    (dateFrom)
--   <DODATUMA>  Period end   YYYYMMDD    (dateTo)
--   <BRANCH>    Company branch number    (companyId)
-- ============================================================
-- Column order matches MIMV_DETALJ:
--   OIB_OBVEZNIKA, DATUM_PP, SIFRA_OBRASCA_PP, REDNI_BROJ_PP, REDNI_BROJ_PP_PROM,
--   SIFRA_VOZILA, STATUS_VOZILA, VRSTA_VOZILA, MARKA_VOZILA, TIP_VARIJANTA_TRG_NAZIV,
--   VIN_OZNAKA, VRSTA_GORIVA, DATUM_PRVE_REGISTRACIJE, PROSJ_EMISIJA_CO2, RAZINA_EMISIJE,
--   RADNI_OBUJAM_MOTORA, SNAGA_MOTORA, PRODAJNA_CIJENA, BROJ_PRIJEDJENIH_KM,
--   KAMPER, PLUG_IN, VOZILO_71, VOZILO_81, TESTNO_VOZILO, DEPRECIJACIJA,
--   POREZNI_OBVEZNIK, OIB, BROJ_RACUNA, DATUM_IZDAVANJA_RACUNA, OBRACUNATI_IZNOS_PP
-- ============================================================

select
'<OIB>'                                                                                     OIB_OBVEZNIKA,
<DATUM>                                                                                     DATUM_PP,
'<SIFOBR>'                                                                                  SIFRA_OBRASCA_PP,
<RBR>                                                                                       REDNI_BROJ_PP,
<RBRPROM>                                                                                   REDNI_BROJ_PP_PROM,
hfskey                                                                                      SIFRA_VOZILA,
case when HFSFZGART in ('A', 'G') then 'R'
     when HFSFZGART = 'N'         then 'N'
     when HFSFZGART = 'V'         then 'NT'
     else HFSFZGART
end                                                                                         STATUS_VOZILA,
vrsta_vozila                                                                                VRSTA_VOZILA,
ifnull(CHYSAA, '')                                                                          MARKA_VOZILA,
left(trim(left(naziv, nazlen)) || ', ' || dodatak, 50)                                      TIP_VARIJANTA_TRG_NAZIV,
trim(substr(HFSFGST, 4, 17))                                                                VIN_OZNAKA,
case when upper(hfstrb) = 'N' then 'D' else hfstrb end                                     VRSTA_GORIVA,
HFSZULDAT                                                                                   DATUM_PRVE_REGISTRACIJE,
HFSCO2                                                                                      PROSJ_EMISIJA_CO2,
razina_emisije                                                                              RAZINA_EMISIJE,
case when '<SIFPOD>' = '0000F7' then HFSHUBR else 0 end                                    RADNI_OBUJAM_MOTORA,
HFSKW                                                                                       SNAGA_MOTORA,
prod_cijena                                                                                 PRODAJNA_CIJENA,
hfskm                                                                                       BROJ_PRIJEDJENIH_KM,
''                                                                                          KAMPER,
0                                                                                           PLUG_IN,
''                                                                                          VOZILO_71,
''                                                                                          VOZILO_81,
dec('0', 7, 2)                                                                              TESTNO_VOZILO,
dec('0', 7, 2)                                                                              DEPRECIJACIJA,
left(case when hfbrpnm1 = '' then HFKKFNM1 else hfbrpnm1 end, 80)                          POREZNI_OBVEZNIK,
case when oib_poreznog is not null then left(oib_poreznog, 11) else '' end                  OIB,
left(trim(char(HFBBLFA)) || '/' || trim(char(case when t1.aenbet > 0 then t1.aenbet else t1.anlbet end)) || '/6', 30) BROJ_RACUNA,
HFBDATFTR                                                                                   DATUM_IZDAVANJA_RACUNA,
obracunati_pp                                                                               OBRACUNATI_IZNOS_PP
from
(select
    hfskey,
    HFSFZGART,
    '1'                                                                                     as vrsta_vozila,
    CHYSAA,
    hfptxt                                                                                  as naziv,
    trim(case when upper(hfptxt) like '%AUTOM%' then 'automatski' else 'rucni' end)
        || ', ' ||
    trim(case when PCDLACKART = 'M' then 'metalik' else 'obicna' end)                      as dodatak,
    50 - length(
        trim(case when upper(hfptxt) like '%AUTOM%' then 'automatski' else 'rucni' end)
        || ', ' ||
        trim(case when PCDLACKART = 'M' then 'metalik' else 'obicna' end)
    ) - 2                                                                                   as nazlen,
    HFSFGST,
    hfstrb,
    HFSCO2,
    HFSHUBR,
    HFSKW,
    HFSZULDAT,
    hfskm,
    case when HFBDATFTR >= 20170101
         then ''
         else case when upper(hfstrb) = 'N' then 'VI' else '' end
    end                                                                                     as razina_emisije,
    case when hfbrpnm1 = '' then HFKKFNM1   else hfbrpnm1   end                            as porezni_obveznik,
    case when hfbrpnm1 = '' then HFKKFUIDNR else hfbrpuidnr end                            as oib_poreznog,
    HFBBLFA,
    t1.aenbet,
    t1.anlbet,
    HFBDATFTR,
    case when HBJHRTBAS is null then HFKHRTBAS else HBJHRTBAS end                          as prod_cijena,
    case when HBJHRTVAL is null then HFKHRTVAL else HBJHRTVAL end                          as obracunati_pp
from
    hfs hfs
    left join hfk on HFSKEY = HFKKEY
    left join perv on HFKVK = pebper
    join hfb t1 on HFSKEY = HFBKEY
    left join hbj t2 on
        HFBKEY = HBJHFSKEY1 and
        HFBBLFA = HBJBNR and
        HBJBDAT = HFBDATFTR and
        HBJAKT in ('FA', 'FG')
    left join hfp on HFSKEY = HFPHFSNR
    join mar on marmar = HFSMAR
    left join pcd on hfsfar = pcdpcd and hfsmar = pcdmar
    left join ivasxt.klchcpp on upper(martxt) = CHL6AQ
where
    HFBDATFTR between <ODDATUMA> and <DODATUMA>
    and (
        case when HFKDATZOLL <> 0 then HFKDATZOLL end between <ODDATUMA> and <DODATUMA>
        or HFKDATZOLL = 0
        or HFKDATZOLL >= <DODATUMA>
    )
    and HFSVKSTS = 'F'
    and HFPPART = 'F'
    and HFKHRTKZ <> ''
    and (HFKHRTVAL <> 0 or (HBJHRTVAL is not null and HBJHRTVAL <> 0))
    and case when HBJHRTBAS is null then HFKHRTBAS else HBJHRTBAS end > 0
    and (
        HFSFZGART in (
            select tipvoz from (
                select 'N' tipvoz from sysibm.sysdummy1
                union all
                select 'V' tipvoz from sysibm.sysdummy1
            ) t
            where exists (
                select * from ivasdet
                where aupgm = 'KMDPDFR'
                and locate('MV02', auhhap) > 0
            )
        )
        or
        HFSFZGART in (
            select tipvoz from (
                select 'A' tipvoz from sysibm.sysdummy1
                union all
                select 'G' tipvoz from sysibm.sysdummy1
            ) t
            where exists (
                select * from ivasdet
                where aupgm = 'KMDPDFR'
                and locate('MV03', auhhap) > 0
            )
        )
    )
    and case when t1.aenbet > 0 then t1.aenbet else t1.anlbet end = <BRANCH>
    and (char(hfskey) not in ('') or '' in (''))
order by pesname, HFSMAR, HFBDATFTR
) t

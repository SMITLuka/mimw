-- ============================================================
-- INSERT INTO IVAS<SIFPOD>.KMAQCPP  (MI-MV Detalj)
-- ============================================================
-- Placeholders (replaced at runtime):
--   <SIFPOD>    – mandatorId from request header  (e.g. 0000B0)
--   <OIB>       – OIB obveznika                   (e.g. 30985203273)
--   <DATUM>     – DatumPP as integer YYYYMMDD      (e.g. 20260401)
--   <SIFOBR>    – SifraObrascaPP                  (e.g. 401 or 405)
--   <RBR>       – RedniBrojPP                     (e.g. 1)
--   <RBRPROM>   – RedniBrojPPPromjena              (e.g. 0)
--   <ODDATUMA>  – Period start as integer YYYYMMDD (e.g. 20260101)  → dateFrom from request body
--   <DODATUMA>  – Period end   as integer YYYYMMDD (e.g. 20260331)  → dateTo   from request body
--   <BRANCH>    – Pantheon company branch number   (e.g. 1)         → companyId from request header
-- ============================================================

insert into ivas<SIFPOD>.kmaqcpp
select '<SIFPOD>' pod, '<OIB>', <DATUM> datum_pod, '<SIFOBR>' sifobr,
<RBR> rbr, <RBRPROM> rbr_prom, hfskey sif_vozila, trim(vrsta_vozila) vrstavoz,
upper(trim(status_vozila)) status_vozila, datum_prve_reg, snaga_motora, prod_cijena,
broj_prijedenih_km, '' as vozilo71, '' as vozilo81,
dec('0', 7, 2) as testvozilo, dec('0', 7, 2) deprecijacija,
obracunati_pp, '', '', '', 0, 0, 0, 0, 0, 0, 0, 'A', '', ''
from
(select
    hfskey,
    '1' as vrsta_vozila,
    case when HFSFZGART in ('A', 'G') then 'R'
         when HFSFZGART = 'N'         then 'N'
         when HFSFZGART = 'V'         then 'NT'
         else HFSFZGART
    end as status_vozila,
    HFSZULDAT as datum_prve_reg,
    HFSKW     as snaga_motora,
    case when HBJHRTBAS is null then HFKHRTBAS else HBJHRTBAS end as prod_cijena,
    hfskm as broj_prijedenih_km,
    case when HBJHRTVAL is null then HFKHRTVAL else HBJHRTVAL end as obracunati_pp
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
    left join ads on (case when hfbrpnm1 = '' then HFKKFKDNR else hfbrpkdnr end) = ADSKEY
    left join hfp on HFSKEY = HFPHFSNR
    join mar on marmar = HFSMAR
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


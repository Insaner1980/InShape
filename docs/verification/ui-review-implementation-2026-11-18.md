# InShapen käyttöliittymäkatselmuksen toteutus

Toteutus perustuu [18.11.2026 katselmussuunnitelmaan](../redesign-monochrome/ui-review-plan-2026-11-18.md). Alla oleva tila kuvaa koodimuutoksia. Testitulokset ovat omassa osiossaan.

| Kohta | Tila | Toteutus |
| --- | --- | --- |
| 1.1 | Toteutettu | Privacy käyttää teeman tekstiväriä ja taustaa. Otsikot ja kappaleet tulevat merkkijonoresursseista. |
| 1.2 | Toteutettu | MeasurementBasis antaa tallennetuille avaimille näytettävät nimet, yksiköt ja arvojen muotoilun. Tuntemattoman tallennetun avaimen varamuotona on paino. |
| 1.3 | Toteutettu | Today piilottaa Routines-osion, kun ainoa rutiini näkyy suosituksena. Tyhjässä päiväkirjassa näkyy rutiinin luonti. |
| 1.4 | Toteutettu | Harjoitteen Edit-painike käyttää ylätunnisteen lisätilaa oikealla. |
| 1.5 | Toteutettu | Ohjelinkit näkyvät omissa riveissään, joissa on verkkotunnus ja ulkoisen linkin kuvake. |
| 1.6 | Toteutettu | Check-in ja History käyttävät EnergyLevel-nimiä. History näyttää energian täytetyssä tunnisteessa. |
| 1.7 | Toteutettu | Tuhoavat dialogipainikkeet saavat tilansa kutsujalta. Soittolistan dialogi säilyttää oman tallennus- ja virheenkäsittelynsä. |
| 2.1 | Sovellettu | Otsikko kohdistetaan vieritysalueen yläreunaan mitattujen sijaintien avulla. Seuraava sarja tuodaan näkyviin BringIntoViewRequesterilla. Otsikon kohdistus tarvitsee tarkan vierityskohdan. |
| 2.2 | Toteutettu | Tyhjän live-treenin pääpainike avaa harjoitevalitsimen. |
| 2.3 | Toteutettu | Treenin Music-kuvake avaa valitsimen suoraan. |
| 2.4 | Toteutettu | Check-in-valikon nimi vaihtuu, kun ensimmäinen harjoite on merkitty tehdyksi. |
| 2.5 | Toteutettu | Vaihtoehtojen jälkeen on haettava harjoitelista. Harjoitteen lisääminen ja vaihtoehdon valitseminen käyttävät samaa listaa. |
| 2.6 | Toteutettu | Now playing näyttää isommat ohjaimet ja saatavilla olevan kansikuvan. Kannettoman kappaleen taustana on PlaybackRibbon. |
| 2.7 | Sovellettu | Menneen treenin tallennustoiminnot on nimetty Save ja Save workout. Aktiivisen harjoitteen pääpainike merkitsee edelleen harjoitteen tehdyksi. |
| 2.8 | Toteutettu | Uudessa treenin jälkeisessä check-inissä riittää rasittavuuden tallennus. Kehokartta avataan Yes-valinnalla ja lisätiedot omasta avausrivistään. |
| 3.1 | Toteutettu | DisclosureRow näyttää vakaan tekstin ja avaus-/sulkutilan. Tekstitoiminnot erottuvat kevyemmällä typografialla ja tehtävään liittyvällä kuvakkeella. |
| 3.2 | Toteutettu | DialogAction yhtenäistää dialogien painikkeet ja niiden sisämarginaalit. Välittömän valinnan dialogin voi perua Cancel-toiminnolla. |
| 3.3 | Toteutettu | Mittaus-, havainto- ja asetustunnisteet ovat täytettyjä. Kehon aluekortilla ei ole reunusta. |
| 3.4 | Toteutettu | History, Library ja Music käyttävät ScreenTabs-välilehtiä. Progress-ajanjakso on kompakti valitsin. |
| 3.5 | Toteutettu | Sivujen osiot käyttävät tavallisia otsikoita. Hiljaiset ryhmäotsikot on lueteltu alla. |
| 3.6 | Toteutettu | Käytöstä poistettu reunapainike käyttää vaimeaa reunaa ja tekstiä. |
| 3.7 | Toteutettu | Poisto-, arkistointi-, hylkäys- ja palautusvahvistukset on merkitty tuhoaviksi. Lomakkeiden pienten kenttäryhmien poisto käyttää suunnitelman mukaista sulkemiskuvaketta. |
| 3.8 | Toteutettu | Rutiineilla ja soittolistoilla on yhteinen vetokahva ja saavutettavat siirtotoiminnot. Soittolistassa poisto tapahtuu rivivalikosta. |
| 3.9 | Toteutettu | Rutiinin ja sijaintien valikot käyttävät kuvakkeellista MenuActionia. |
| 3.10 | Sovellettu | Vaalean teeman passiivisille merkeille on erillinen harmaa. Käytössä ollut surfaceStrong on valkoista vasten noin 1,19:1. Uusi sävy ylittää 1,3:1. |
| 4.1 | Toteutettu | Today käyttää reunapainiketta tyhjälle treenille ja yhteistä väliä osioissa. |
| 4.2 | Toteutettu | Ensimmäisen harjoitteen poistovihje poistuu onnistuneen sarjapoiston jälkeen olemassa olevan käyttöliittymäasetustiedoston avulla. Muistiinpanolla on nimike ja ohjeteksti. |
| 4.3 | Toteutettu | Treenin nimi ja päivä ovat eri riveillä. Health Connect -rivi kertoo saatavuudesta. |
| 4.4 | Toteutettu | Kestokenttä on täysleveä ja aikavaihto käyttää kellokuvaketta. |
| 4.5 | Toteutettu | Yhteenvetorivillä on suurempi sisämarginaali. Koriste jää pois tyhjästä historiasta. Kalenteriviikkojen välit pienenevät ja päivän kosketuskorkeus on vähintään 48 dp. |
| 4.6 | Sovellettu | Kestokenttä säilyy muokattavana. Laskettu aika näkyy erikseen vain, kun se poikkeaa kentän minuuttiarvosta tai lopetus on ennen aloitusta. Sarjojen muokkaus näyttää saraketaulukon otsikot. |
| 4.7 | Toteutettu | Measure korvaa päällekkäiset Progress-valitsimet. Ennätysten arvo korostuu ja kipukaavio käyttää neljää sanallista tasoa. Tasainen sarja näkyy pisteinä. |
| 4.8 | Toteutettu | Asetuskentillä on yhteiset sarakeotsikot ja yksilölliset saavutettavat nimet. Linkin poisto on kuvake. Vaihtoehdot valitaan hausta, ja uuden harjoitteen kuvaohje on Photos-osiossa. |
| 4.9 | Toteutettu | Rutiinin kuvaus on omalla rivillään. Sijainti näkyy harjoitetyyppien yläpuolella myös tyhjässä haussa. Tyhjä rutiinilista tarjoaa luontipainikkeen. |
| 4.10 | Toteutettu | Palautuksesta keskeytynyt muistutus näkyy pois päältä ja sen voi ottaa käyttöön samalta riviltä. Health Connectin asetukset näkyvät, kun palvelu on saatavilla. |
| 4.11 | Toteutettu | Rutiinilista selittää sarjatavoitteet, yhteenvetokortin tiedot ovat eri riveillä ja Save routine näkyy muokatulle luonnokselle. Cancel hylkää tietodialogin muutokset. |
| 4.12 | Toteutettu | Kehokartan Front ja Back ovat pienet valitsimet. Aluekortin nimi korostuu ja kartan alla on tiivis ohjeväli. |
| 4.13 | Toteutettu | Music käyttää yhteisiä kenttiä, tyhjiä tiloja ja toimintopalkin välejä. Jonossa nykyinen kappale näkyy erikseen, ja jo lisätty kappale merkitään soittolistavalinnassa. |
| 4.14 | Toteutettu | Ajanjakso vahvistetaan Apply-painikkeella. CSV-kuvaus kertoo ZIP-tiedoston sisällön. |
| 4.15 | Toteutettu | Libraryn kuvitukset keskittyvät samankokoisiin ruutuihin niissä asetteluissa, joissa kuvitus näytetään. |
| 5 | Toteutettu | Käyttöliittymätekstit noudattavat katselmuksen sanavalintoja. Säilytetyt arvot eivät muutu. |
| 6 | Tarkastettu lähdekoodista | Room-skeemaa ja varmuuskopion muotoa ei muutettu. Ajastimen toteutus, kuvitustiedostot ja Health Connectin tietovirta säilyivät. |
| 7 | Katso tarkistukset | Kohdennetut testit lisättiin ja vanhojen testien valitsimet päivitettiin. PROJECT.md kuvaa yhteiset komponentit. |

Hiljaiset ryhmäotsikot säilyvät Exercise Editorin koneasetuksissa, asetus- ja arvosarakkeissa, muistiinpanoissa, linkeissä ja vaihtoehdoissa. Ne kuvaavat alla olevia lomakekenttiä tai valintoja. SelectField käyttää GroupLabelia kentän nimenä. Vaihtovalitsimen All exercises on hiljainen alaotsikko suunnitelman kohdan 2.5 mukaan.

Projektin Compose BOM 2026.09.00 käyttää Foundation 1.12.1:tä ja Material 3 1.4.0:aa. Vierityksen, välilehtien, dialogipainikkeiden, liukusäätimen ja alapaneelin toteutus tarkistettiin näiden rajapintojen perusteella. [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [Vieritys](https://developer.android.com/reference/kotlin/androidx/compose/foundation/relocation/package-summary), [Välilehdet](https://developer.android.com/reference/kotlin/androidx/compose/material3/SecondaryTabRow.composable), [Painikkeet](https://developer.android.com/reference/kotlin/androidx/compose/material3/ButtonDefaults), [Liukusäädin](https://developer.android.com/reference/kotlin/androidx/compose/material3/Slider.composable), [Alapaneeli](https://developer.android.com/reference/kotlin/androidx/compose/material3/ModalBottomSheet.composable)

## Tarkistukset

- Gradle vahvisti `assembleDebug`-, `assembleDebugAndroidTest`-, `testDebugUnitTest`- ja `lintDebug`-tehtävät. Unit-testejä oli 120, kaikki läpäisivät. Android Lintissä oli 10 varoitusta ja 0 virhettä. KSP ajettiin asetuksella `-Pksp.incremental=false`, koska ensimmäisessä ajossa generoitu hakemisto puuttui.
- Ensimmäinen koko emulaattoriajo suoritti 72 testiä. 66 läpäisi ja 6 epäonnistui. Neljä testiä päivitettiin vastaamaan suunniteltua käyttöliittymää. Välilehtitekstin mittaus ja aikadialogin kahden Done-painikkeen tilanne korjattiin. Uusinta-ajossa läpäisi 71/72, mukaan lukien edellisen ajon kuusi epäonnistunutta testiä. LauncherWidgetTestin käynnistystoiminto aikakatkaistiin toisella avauskerralla. Sama testi läpäisi erillisessä uusinnassa. Viimeisimmän minisoitinmuutoksen jälkeen MusicUiTestin kaikki neljä testiä läpäisivät.
- Asettelutestit läpäisivät viimeisimmällä APK:lla 12 demonäkymää kolmessa asetuksessa. Mitatut asetukset olivat 320 dp ja 100 % tummassa teemassa, 411 dp ja 200 % vaaleassa teemassa sekä 320 dp ja 200 % tummassa teemassa. Minisoittimen kappaleen ja esittäjän leikkautuminen korjattiin asettamalla suurikokoisen tekstin alle oma painikerivi.
- Emulaattorissa vahvistettiin myös, että kolmas sarja näkyy kokonaan minisoittimen yläpuolella ja Exercise done nostaa seuraavan harjoitteen näkyviin tiiviin kellon kanssa.
- Vertailukansiossa on kaikki 148 lähtökuvien tiedostonimeä, mukaan lukien 20 vaalean teeman kuvaa. Kuvaukset tehtiin API 36:n medium_phone-emulaattorilla 1080 × 2424 pikselin koossa. Jokaisen PNG-tiedoston koko ja tiedostorakenne tarkistettiin. Kaikki tiedostonimet täsmäsivät lähtöaineistoon ja tiedostot erosivat toisistaan. Harjoitekuvan lisäämisen näkymissä käytettiin sovelluksen mukana olevaa harjoitekuvaa testikuvana. [Vertailukuvat](../../reports/screenshots-2026-11-18-pixel-9-after/)

## Tiedostot

- `app/src/main/java/com/emmah/inshape/AppContainer.kt`
- `app/src/main/java/com/emmah/inshape/data/JournalModels.kt`
- `app/src/main/java/com/emmah/inshape/data/MeasurementBasis.kt`
- `app/src/main/java/com/emmah/inshape/feature/checkin/BodyMap.kt`
- `app/src/main/java/com/emmah/inshape/feature/checkin/CheckInLevels.kt`
- `app/src/main/java/com/emmah/inshape/feature/checkin/CheckInScreen.kt`
- `app/src/main/java/com/emmah/inshape/feature/history/HistoryScreen.kt`
- `app/src/main/java/com/emmah/inshape/feature/library/ExerciseEditor.kt`
- `app/src/main/java/com/emmah/inshape/feature/library/ExerciseScreen.kt`
- `app/src/main/java/com/emmah/inshape/feature/library/ExerciseThumbnail.kt`
- `app/src/main/java/com/emmah/inshape/feature/library/LibraryScreen.kt`
- `app/src/main/java/com/emmah/inshape/feature/library/RoutineEditor.kt`
- `app/src/main/java/com/emmah/inshape/feature/music/MusicArtwork.kt`
- `app/src/main/java/com/emmah/inshape/feature/music/MusicScreens.kt`
- `app/src/main/java/com/emmah/inshape/feature/music/PlayerScreens.kt`
- `app/src/main/java/com/emmah/inshape/feature/music/PlaylistEditors.kt`
- `app/src/main/java/com/emmah/inshape/feature/progress/ProgressCalculator.kt`
- `app/src/main/java/com/emmah/inshape/feature/progress/ProgressCharts.kt`
- `app/src/main/java/com/emmah/inshape/feature/progress/ProgressScreen.kt`
- `app/src/main/java/com/emmah/inshape/feature/settings/SettingsScreen.kt`
- `app/src/main/java/com/emmah/inshape/feature/today/TodayScreen.kt`
- `app/src/main/java/com/emmah/inshape/feature/workout/FinishScreen.kt`
- `app/src/main/java/com/emmah/inshape/feature/workout/SetRow.kt`
- `app/src/main/java/com/emmah/inshape/feature/workout/WorkoutScreen.kt`
- `app/src/main/java/com/emmah/inshape/feature/workout/WorkoutTimingEditor.kt`
- `app/src/main/java/com/emmah/inshape/health/HealthControls.kt`
- `app/src/main/java/com/emmah/inshape/health/HealthPrivacyActivity.kt`
- `app/src/main/java/com/emmah/inshape/navigation/AppNavigation.kt`
- `app/src/main/java/com/emmah/inshape/transfer/BackupControls.kt`
- `app/src/main/java/com/emmah/inshape/transfer/ExportScreen.kt`
- `app/src/main/java/com/emmah/inshape/ui/components/Buttons.kt`
- `app/src/main/java/com/emmah/inshape/ui/components/Controls.kt`
- `app/src/main/java/com/emmah/inshape/ui/components/Editors.kt`
- `app/src/main/java/com/emmah/inshape/ui/components/Fields.kt`
- `app/src/main/java/com/emmah/inshape/ui/components/Lists.kt`
- `app/src/main/java/com/emmah/inshape/ui/components/ReorderControls.kt`
- `app/src/main/java/com/emmah/inshape/ui/components/SelectionControls.kt`
- `app/src/main/java/com/emmah/inshape/ui/components/TimePickers.kt`
- `app/src/main/java/com/emmah/inshape/ui/theme/Dimens.kt`
- `app/src/main/java/com/emmah/inshape/ui/theme/InShapeColors.kt`
- `app/src/androidTest/java/com/emmah/inshape/EndToEndJournalTest.kt`
- `app/src/androidTest/java/com/emmah/inshape/MusicUiTest.kt`
- `app/src/androidTest/java/com/emmah/inshape/ProgressHistoryCoverageTest.kt`
- `app/src/androidTest/java/com/emmah/inshape/RedesignInteractionTest.kt`
- `app/src/androidTest/java/com/emmah/inshape/ReferenceLayoutTest.kt`
- `app/src/test/java/com/emmah/inshape/data/MeasurementBasisTest.kt`
- `app/src/test/java/com/emmah/inshape/feature/checkin/CheckInLevelsTest.kt`
- `app/src/test/java/com/emmah/inshape/feature/library/LastRecordedValuesTest.kt`
- `app/src/test/java/com/emmah/inshape/feature/library/RoutineTargetSummaryTest.kt`
- `app/src/main/res/values/strings.xml`
- `PROJECT.md`
- `docs/verification/ui-review-implementation-2026-11-18.md`

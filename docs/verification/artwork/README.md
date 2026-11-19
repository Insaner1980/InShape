# InShape-kuvituksen toteutus ja varmennus

18.11.2026. Laitetesti tehtiin Pixel 9:llä, Android 17 (API 37), koontiversio CP4A.261105.040.A1. Erillinen debug-sovellustunnus piti laitteen oman InShape-asennuksen ja testidatan erillään.

## Resurssit

Kaikki 64 lähdekuvaa löytyvät toteutuksesta. Julkaisuresurssien linkitys sisälsi 57 tavuntarkkaa WebP-kopiota ja viisi vektoria. Kahden kehokartan 48 polkua on siirretty sovelluskoodiin. Kehokartan piirto ja osumat käyttävät samaa peilattua geometriaa. Julkaisun resurssitaulukossa oli 62 kuvitusresurssia, ja vanhoja demokansia oli nolla. [44 harjoituskuvan kooste](equipment-contact-sheet.png)

## Kuvakaappaukset

| Näkymä | Vaalea | Tumma |
| --- | --- | --- |
| 390 dp, 100 %, treeni | [Today, treenikello ja tallennus](390-100-light-training.png) | [Aktiivinen Today ja treeni](390-100-dark-training.png) |
| 390 dp, 100 %, päiväkirja | [Harjoitus, kehokartta, historia, edistyminen ja rutiini](390-100-light-journal.png) | [Kehokartta ja harjoitus](390-100-dark-journal.png) |
| 390 dp, 100 %, kirjasto | [Harjoituslista ja valitsin](390-100-light-library-picker.png) | |
| 390 dp, 100 %, musiikki ja teema | [Soittolistat, Night Drive, treenisoitin ja teemat](390-100-light-music-appearance.png) | [Night Drive ja teemat](390-100-dark-music-appearance.png) |
| 320 dp, 200 % | [Kompakti, vaalea](320-200-light-compact.png) | [Kompakti, tumma](320-200-dark-compact.png) |

Kompaktissa näkymässä laitekuva ja Night Drive -kansi siirtyvät omalle rivilleen. Pitkä historiayhteenvedon kesto piilottaa paperikulman. 320 dp:n leveydellä ja 200 %:n fontilla osa nykyisistä alavalikon englanninkielisistä nimistä rivittyy sanan keskeltä.

Värien laskennallinen vähimmäiskontrasti oli Today-kortin vaalealle toissijaiselle tekstille 5,01:1 ja käännetyn aktiivisen kortin toissijaiselle tekstille 7,49:1. Aktiivikortin odottavan edistymissegmentin reunus ylsi arvoon 3,50:1. Tumman kehokartan ääriviivojen kontrasti oli vähintään 4,08:1. Night Drive -bannerin valkoisen tekstin suojatulla alueella kontrasti on vähintään 9,23:1 valkoisellakin kuvapikselillä. Luvut perustuvat sävyihin ja kerrosten peittävyyteen.

## Health Connect

Tavallisen InShape-käynnistimen asetuksissa kaikki neljä Health Connect -toimintoa olivat valittavissa. **Manage Health Connect** avasi Pixelin järjestelmätason Health Connect -sovelluksen. Demon asetuksissa näkyvä ilmoitus johtuu demon erillisestä ympäristöstä, jossa integraatiot ovat pois päältä. Testi ei myöntänyt terveystietojen käyttöoikeuksia.

## Testitulokset

| Tarkistus | Tulos |
| --- | --- |
| Koonti ja lint | Debug-sovellus ja testi-APK koottiin normaaleilla pakettitunnuksilla. Android Lintissä oli 0 virhettä. |
| JVM-testit | 115 läpäisi, 0 ohitettua. Harjoituskuvien testi kattoi kaikki 44 kartoitusta, kymmenen alkuperäistä demon harjoitusta, nimien normalisoinnin ja rutiinikuvan valinnan. |
| Pixel 9:n toiminnalliset testit | Viimeisessä yhteisajossa 41 läpäisi. Kehokartan 23 tunnistetta olivat saavutettavissa. Kansitesti käytti MP3:n upotettua kuvaa ja SAF-osoitetta, johon testisovelluksella ei ollut käyttöoikeutta. Testit kattoivat myös valokuvan poistamisen, valinnan säilymisen historiassa ja teeman vaihtamisen. |
| Pystyasettelu | 14 näkymää läpäisi jokaisen seitsemästä yhdistelmästä. 390 dp / 100 %, 390 dp / 200 % ja 320 dp / 200 % ajettiin molemmissa teemoissa. 320 dp / 100 % ajettiin vaaleana. Musiikin kaksi toimintotestiä läpäisivät lisäksi 320 dp / 200 % molemmissa teemoissa. |
| Vaaka ja RTL | Neljä näkymää läpäisi vaakasuunnassa 923 dp:n leveydellä ja 115 %:n fontilla. Kehokartan kolme testiä läpäisivät arabian sovellusasetuksilla. [Vaakatreeni ja RTL-soittolista](rtl-landscape.png) |
| Pixel Launcher | Käynnistys ja jatkaminen avasivat saman treenin. Leveän lepotilan kuvitus näkyi molemmissa järjestelmäteemoissa. Aktiivinen widget poisti kuvituksen ja näytti kellon etenevän 11:37:stä 11:41:een. [Leveä lepotila](pixel-launcher.png), [aktiivinen kello](pixel-launcher-active-clock.png) |

Pixelin Android 17 tarvitsi Espresso 3.7.0:n laitetesteihin, koska vanhempi testiriippuvuus käytti poistettua InputManager-metodia. Teemanvaihdon värit tarkistettiin sovelluksen oikeassa teema-aktiviteetissa. [AndroidX Testin julkaisutiedot](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)

## Varmennuksen rajat

TalkBackin puhetta, vanhempaa widgetvalitsinta ja kapeaa widgetiä ei vahvistettu laitteella. Pixelin kotinäytön koonsäätökahva ei ollut automaation saavutettavissa. Oikean musiikkikansion käyttöluvan poistamista ja uudelleenhakua sekä henkilökohtaisen valokuva-arkiston palautusta ei ajettu Pixelillä. Kansi- ja kuvavaraston testit käyttivät erillisiä testitiedostoja.

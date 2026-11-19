# InShape artwork and gradient implementation plan

**Design handoff, 18 November 2026.** Introduce the approved sage, charcoal and petrol artwork throughout the existing Android journal. The release asset set has 64 source images in seven folders. The latest 44 exercise images supersede the engraved equipment in the original screen concepts. Use the concepts for composition and the assets in this repository for the actual image content.

## 1. Implementation baseline

| Area | Current behavior verified in the working tree | Implementation boundary |
| --- | --- | --- |
| Today and workout | Today chooses a populated routine through `suggestedRoutine`. An active workout takes precedence. The light-theme active card already inverts its ink. The workout screen moves its clock into the top bar as the main clock scrolls away. | Attach artwork to those existing branches. The journal still determines the action, clock and progress. |
| Library | An exercise has editable names, a category, a separate equipment identity and optional personal photos. The exercise list already has a leading photo slot. Exercise detail supports full-screen viewing, multiple photos, replacement and removal. | Resolve a bundled illustration at display time. Keep photo management and recorded exercise identity intact. |
| Routine | A routine has a title, optional planned duration and ordered exercise references. The routine editor holds unsaved changes in a draft. | Derive the routine picture from the items and duration that the user is currently viewing. |
| History | Calendar and Progress share the History destination. Calendar computes three summary values. Progress distinguishes attendance for the selected period from exercise and body observations. | Add art around the relevant summary or empty message. Keep dates, chart marks and metric calculations legible. |
| Check-in | The front and back body views currently draw on a 100 × 240 logical canvas. Touch detection uses region paths. The supplied art uses a 240 × 392 viewport. | Replace the drawing and hit geometry together. Retain existing body-region IDs and recorded observations. |
| Music | Music has its own Room index. Playlists have IDs, names and ordered tracks. Playlist rows and detail headers request art from available tracks. The existing decoder caches bitmaps and the renderer applies a monochrome filter. | Preserve access to personal cover art. Provide local fallback illustrations for release playlists and deterministic covers for the three demo playlists. |
| Theme and widget | The app has System, Light and Dark modes. The Glance widget has 160 × 130 dp and 250 × 130 dp layouts and a native chronometer. | Theme the app in Compose and the home-screen widget in Glance. Preserve the widget’s own host day/night behavior. |

Work from the current feature branch, including the music implementation in the working tree. The approved gradients extend the older monochrome plan specifically on the surfaces listed below. Continue using Inter, the existing foreground and error tokens, button shapes and navigation hierarchy. Apply the image selection rules locally. They require no server, new permission, Room migration, seeded user records or change to the journal backup format.

[Earlier UI plan](ui-polish-plan.md) and [current project behavior](../../PROJECT.md)

## 2. Asset delivery contract

All asset links below resolve relative to this document. Keep the existing artwork folder as the editable source. For the Android package, use the following mechanical naming rule so every screen can refer to an explicit resource identifier.

| Source group | Count | Android representation |
| --- | ---: | --- |
| Training | 6 | Four WebPs and two scalable motion overlays |
| Exercise equipment | 44 | Transparent 768 × 768 WebPs |
| History and progress | 3 | Transparent WebPs |
| Body check-in | 2 | Interactive named paths derived from the two SVGs |
| Music | 5 | Opaque photographic WebPs, including the square and wide Night Drive crops |
| Settings | 3 | Scalable theme preview illustrations |
| Home widget | 1 | Transparent WebP |
| **Total** | **64** | **57 WebPs and 7 SVG sources** |

1. Copy each WebP once into `app/src/main/res/drawable-nodpi/` as `art_<source-folder>_<filename>`. For example, the release identifier for `exercises/leg_press.webp` is `R.drawable.art_exercises_leg_press`. Preserve the supplied aspect ratio, transparency and compression. Reference drawable IDs directly so the resource shrinker can see every entry.
2. Convert the two motion SVGs and the three theme SVGs into matching, prefixed vector drawables under `res/drawable/`. Preserve viewports, clipping, transforms and gradient opacity. For the split System tile, inspect the diagonal boundary after conversion. If importing a particular vector loses its clip or gradient, flatten that one decorative asset to a transparent WebP at three times its maximum specified display size. Package one working representation per source.
3. Translate the two body SVGs into shared drawing and hit-test geometry as specified in section 6. Keep the named regions addressable. The interactive path implementation is the runtime representation of these two images.
4. Keep the UI screenshots, the 44-item equipment contact sheet, and the old debug artwork out of the release resource set. Existing actual photos remain user files. Artwork references stay in UI code, outside photo records, workout history, exports, backups and Health Connect payloads.
5. At library thumbnail size, use a sampled bitmap sized for the rendered pixel bounds and a bounded cache, or an equivalent existing thumbnail mechanism. Bind decoding to the visible item and target size. A 768 × 768 ARGB bitmap uses about 2.25 MiB decoded. Loading all 44 together would use about 99 MiB before other images. Load detail-size art only for the open detail. Keep motion drawings and static image work outside the changing clock text.

[Android vector resource guidance](https://developer.android.com/studio/write/vector-asset-studio)

## 3. Color, layering and small-screen rules

The following color values and dimensions are proposed implementation values. All stop positions are fractions of the painted surface. Use sRGB colors at full opacity unless an alpha is specified. The gradient belongs to the local content surface. The page around it uses the existing theme background.

| Treatment | Light surface | Dark surface | Geometry and intended use |
| --- | --- | --- | --- |
| Routine and idle widget | 0.00 `#FFFEFA`, 0.48 `#F6F5ED`, 1.00 `#DCE5D5` | 0.00 `#141817`, 0.48 `#1A2422`, 1.00 `#263B35` | Horizontal from text at start toward equipment at end. Today suggestion, routine editor summary, wide idle widget. |
| Active workout | 0.00 `#FFFFFF`, 0.55 `#FAFBF8`, 1.00 `#E6EEE2` | 0.00 `#121617`, 0.55 `#162326`, 1.00 `#17363A` | Horizontal clock band. The active **Today** card uses the dark treatment in both themes to retain its existing inverted light-theme state. |
| Completed workout | 0.00 `#FFFEFB`, 0.55 `#F3F4ED`, 1.00 `#DAE4DB` | 0.00 `#0B0B0B`, 0.55 `#101716`, 1.00 `#203630` | Horizontal behind the finished-workout summary and towel. Fade the bottom 12 dp into the surrounding page when the header has no visible border. |
| Paper | 0.00 `#FFFCF5`, 0.60 `#F6F2E8`, 1.00 `#E2E6D6` | 0.00 `#191B18`, 0.60 `#20251F`, 1.00 `#2B342B` | Shallow horizontal wash in calendar totals and the empty attendance panel. |
| Equipment detail and attendance | 0.00 `#F8F9F6`, 0.55 `#EDF1EC`, 1.00 `#DFE7E1` | 0.00 `#161A19`, 0.55 `#1E2929`, 1.00 `#294143` | Horizontal behind the large machine cutout or the stack of plates. Library rows use their ordinary list background. |
| Body halo | Center `#E6EBDB` at 0.55 alpha, transparent edge | Center `#53665B` at 0.32 alpha, transparent edge | Center behind the torso. Ellipse width 72% of the map container, maximum 250 dp. Height 82% of the drawn body. Entire figure stays above the halo. |
| Photographic cover | Same in both themes | Same in both themes | Display photos in color. For **overlaid white text** add a black scrim from 84% alpha at the text edge through 72% at 0.45 to 12% at 1.00. Restrict text to the protected part. Use a separate solid text surface when it cannot fit. |

**Painting order.** Clip to the existing host shape, paint the local base and gradient, draw the illustration in its own bounds, then draw the foreground and controls. Place the existing border last. Reserve a distinct text column or clear space around centered numerals. Use `ContentScale.Fit` for equipment and the body, and `Crop` only for photographic covers or intentionally edge-cropped decorative textures. Respect the source composition of right-weighted wide banners. Their transparent area is part of their design. Avoid shrinking an already right-weighted full-width graphic into a tiny end slot. Anchor to logical end, mirror placement in RTL and preserve the orientation of photographs and anatomical artwork. Only the abstract motion curves may be mirrored. Use a scrap of empty space or hide the decorative image if a source composition cannot be mirrored cleanly.

**Layout decision.** Measure the inner content width after screen and card padding. For editorial headers, a two-column layout requires at least 300 dp of inner width, a text column of at least 172 dp, 12 dp of separation and font scale below 1.3. Allocate at most 40% of the width to art. If the heading or status still collides at measurement, use the compact rule. In compact layouts, omit decorative still lifes and curves first. Keep meaningful equipment detail, body maps and album covers in a separate row when necessary. Heights below are minima or visual targets. Text determines the final height. Keep the existing 600 dp page cap and 48 dp interactive targets.

Use the existing light text `#0B0B0B` and secondary `#5E5E5E` on light washes. Use dark-theme foregrounds `#F5F5F5` and `#A3A3A3` on dark washes. In the inverted light-theme Today card use the existing inverse foreground and button behavior. For each foreground, check the worst actual background under its glyphs, including photos and scrims. Target 4.5:1 for ordinary text and 3:1 for large text and meaningful non-text marks. If a label fails, give it a solid backing or move the art. Keep gradients static. Retain existing progress-bar, chart, error, selection and focus semantics. Keep the left edges of cards free of decorative stripes, and use spacing or separate lines between values.

[Compose brush and gradient guidance](https://developer.android.com/develop/ui/compose/graphics/draw/brush)

## 4. Screen-by-screen placement

### Today, routines and the active workout

| Surface and state | Asset and placement | Behavior at the edges |
| --- | --- | --- |
| Today, suggested mobility session | [Mat and strap](artwork/training/mobility_mat.webp). Place the whole roll at the end of the suggestion header, up to 136 × 88 dp. Keep the existing eyebrow, routine title and exercise count at start. Full-width Start workout sits below both columns. | Use the routine rule below. The separate Start empty workout action remains below the card. |
| Today, suggested strength session | [Barbell](artwork/training/strength_barbell.webp). Anchor its cropped plate flush to the end edge of a 96 dp target header using the routine wash. Keep the metal sleeve outside the text column. | Crop the outer plate edge intentionally. Do not crop a user photo into this slot. |
| Today, suggested short session | [Kettlebell and rope](artwork/training/short_session.webp). Use the same header and start action; fit the cluster into the trailing region, aligned bottom-end. | With no populated routine and no active workout, show the existing Start a workout copy and button on the routine wash alone. |
| Today, workout in progress | [Dark motion curves](artwork/training/motion_dark.svg) on the dark active card in both themes. Put the motif at the end of the title/timer area, at most 44% of header width and 64 dp high. | Keep started time, progress segments, count and Resume workout entirely clear. If timing needs review, the status has priority over curves. Preserve error and retry presentation. |
| Library, routine editor | Reuse the same mat, barbell or kettlebell choice in the summary immediately under the app bar. Target 72–88 dp high. Place count, duration and optional description at start. | Resolve from the current draft including unsaved item edits. Empty draft gets a plain summary. Keep the seven-row demo list, reorder grips, menus, Save routine and Start routine in their existing order. Keep routine index rows text-led. |
| Workout, main timer | [Light motion curves](artwork/training/motion_light.svg) on a light active wash and the dark curves on a dark wash. Anchor a 40% width end overlay within the measured timer column. Preserve its centered alignment. | Suppress curves for missing/unreliable timing, compact width or large type. The measured main clock still determines when the compact top-bar timer appears. Keep set fields, IME actions, progress segments, mini-player and sticky action clear. |
| Finish, recorded workout | [Towel and locker key](artwork/training/workout_finished.webp). Place behind the end of the duration/date area below the check mark, above the two metrics. Use the completed-workout wash and a 96–120 dp target art band. | Follow the existing finish screen’s non-empty branch, including its permitted note-only case. Unknown duration retains its text. The empty/discard branch gets no success illustration. Keep the button label Save workout until the save succeeds. |

**Routine selection in production.** Compute once from the current ordered item references, their exercises and the optional planned duration. First choose the mat if every item resolves and all are mobility exercises (case-insensitive exact category `Mobility` or `Liikkuvuus`, or the stretch-mat/shoulder-mobility catalog keys). Next choose the short-session image for a populated routine with 1–20 planned minutes inclusive. Otherwise choose the barbell if a resolved item maps to a strength station or an unmatched item has the explicit category “Strength”. The four bikes, treadmill, elliptical trainer, stair climber, rower and ski ergometer count as cardio. The generic default category “Machine” alone does not establish the training type. Use the plain routine wash for other populated routines, including long cardio-only and unknown custom routines. Empty routines have no art. This priority gives *Mobility at home* the mat at 15 minutes, *Short 20 min* the kettlebell at 20 minutes, and *Full body A* the barbell at 50 minutes. The title, user's identity and clock ticker do not select the image.

[Today owner](../../app/src/main/java/com/emmah/inshape/feature/today/TodayScreen.kt), [routine editor](../../app/src/main/java/com/emmah/inshape/feature/library/RoutineEditor.kt), [workout](../../app/src/main/java/com/emmah/inshape/feature/workout/WorkoutScreen.kt) and [finish](../../app/src/main/java/com/emmah/inshape/feature/workout/FinishScreen.kt)

### Calendar and progress

| Surface | Asset and placement | State decision |
| --- | --- | --- |
| History, Calendar | [Paper corner](artwork/history/paper_calendar.webp) clipped to the bottom-end of the three-value summary directly below the calendar/draft area. Use a 54 × 40 dp end ornament at up to 65% opacity in light mode and 22% in dark. Use the Paper wash under the summary. | Leave the calendar grid, recorded dates and selected-day outline untouched. The summary follows the existing selected period. On stacked metrics or with long duration text, omit the paper corner. Retain the partial-duration explanation below. |
| History, Progress with qualifying attendance | [Stacked plates](artwork/history/stacked_plates.webp) behind the end of the weekly-average header. Target 80 dp high. Use the Equipment and attendance wash only up to the start of the plot. | Render the existing average text on the clear start side. Keep the goal line, bars, partial week, axes and weekly-count action on the normal background. |
| History, Progress without qualifying attendance in the selected range | [Blank training log](artwork/history/empty_training_log.webp) fitted into a 104 × 76 dp end slot beside the existing empty-period message. Use the Paper wash. | Select using the same date-range and attendance condition as the existing message. Switching periods can switch between notebook and plates. A note-only workout, missing metric or sparse body observation must not fabricate a plotted point. Keep detailed exercise and body charts data-led. |

[History owner](../../app/src/main/java/com/emmah/inshape/feature/history/HistoryScreen.kt) and [Progress owner](../../app/src/main/java/com/emmah/inshape/feature/progress/ProgressScreen.kt)

### Library exercise entries, picker and detail

Use the same equipment result in three places. This is the primary placement contract for **every row in section 5**.

1. **Library → Exercises.** Use the leading visual slot ahead of the exercise title. A personal photo uses the existing thumbnail treatment. Otherwise center the matching illustration in a 48 × 48 dp box with 4 dp breathing room. Keep the favorite control, last value and date in their existing positions. At large text or when the value column forces overlap, omit the decorative leading slot. The row remains a single accessible action.
2. **Exercise picker.** Add a 40 × 40 dp leading thumbnail to existing exercise choice rows in the shared picker used for routine/workout additions. Reuse the same photo/illustration order, with the same compact-width escape. Keep titles and the row tap target at least 48 dp high. Buttons for creating an exercise and search/filter feedback retain their current behavior.
3. **Exercise detail.** In the empty-photo panel, put the existing camera prompt and Add photo action at start and the equipment image at end over the Equipment detail wash. Use the existing 200 dp placeholder minimum; allow up to a 160 × 160 dp illustration at regular width. At 320 dp or large type, place the illustration above the prompt at up to 112 × 112 dp and let the column grow. The complete contour, feet, bars and handles must remain visible. Keep the original aspect ratio for personal photos, image viewer and gallery strip. A bundled machine picture has no Replace or Remove photo action.
4. **Historical detail.** If entered with a historical session item, resolve illustrative equipment from that row’s recorded equipment name and exercise name when the existing photo area has no personal image. Preserve the original availability of editing and Add photo when the live exercise has been archived or removed. History rows and recorded setup text still use their recorded values.
5. **Photo states.** Keep the selected personal photo first in detail and the existing first-photo order in the list. Preserve the current loading and failed-photo message/repair route. Only use a bundled illustration as the no-personal-photo state. Removing the final photo reveals the illustration immediately. Restoring a backup with personal photos returns those photos to first priority. A bundled asset never occupies a gallery position.

[Exercise list and picker](../../app/src/main/java/com/emmah/inshape/feature/library/LibraryScreen.kt) and [exercise detail and photo actions](../../app/src/main/java/com/emmah/inshape/feature/library/ExerciseScreen.kt)

## 5. Equipment catalog and matching rules

Use a small, statically referenced catalog in the library/presentation layer. Each table row specifies the actual source file and complete phrases that a user can enter to reach it. The first English phrase is also a human-readable test input. Finnish entries are input aliases for Finnish exercise records. Keep the existing UI language and user's original spelling for display.

**Resolution order.** After the photo rule, look for an exact normalized equipment-name alias, then an exact normalized exercise-name alias. For example, an exercise called “Leg press” with equipment “Horizontal leg press” gets the horizontal machine. For a historical row, use its corresponding saved fields in the same order. Normalize with Unicode NFC, locale-independent lowercase, collapse runs of whitespace and turn punctuation, hyphens and underscores into a single space; trim. Preserve Finnish letters. An alias can intentionally describe a representative machine, as with the unqualified demo names “Leg press” and “Leg curl”. An unqualified “bike”, “press”, “pulldown”, “row” or category “Machine” is insufficient by itself.

For unmatched custom names use the neutral equipment still life in the detail panel as generic decoration. In list and picker rows, omit unknown equipment art to avoid implying that a dumbbell was used. Distinguish a positive dumbbell match from this generic fallback. Names such as “Barbell bench press” and “Cable crunch” must not resolve to the chest-press or abdominal-crunch machines by substring. Exercise IDs and equipment IDs remain the authority for training comparisons. The catalog maps strings to illustrations only.

| Artwork source | English whole-name aliases | Finnish whole-name aliases |
| --- | --- | --- |
| [lat_pulldown.webp](artwork/exercises/lat_pulldown.webp) | Lat pulldown, Pulldown machine, Lat pull-down | Ylätalja |
| [seated_row.webp](artwork/exercises/seated_row.webp) | Seated row, Seated cable row, Low cable row | Alatalja, Alataljasoutu |
| [chest_press.webp](artwork/exercises/chest_press.webp) | Chest press, Seated chest press | Rintaprässi |
| [leg_curl.webp](artwork/exercises/leg_curl.webp) | Seated leg curl, Leg curl | Reisikoukistus istuen, Reiden koukistus istuen |
| [leg_press.webp](artwork/exercises/leg_press.webp) | 45 degree leg press, 45° leg press, Leg press | Jalkaprässi 45°, Jalkaprässi |
| [stationary_bike.webp](artwork/exercises/stationary_bike.webp) | Upright bike, Stationary bike, Warm-up bike | Pystykuntopyörä, Kuntopyörä |
| [assisted_pull_up.webp](artwork/exercises/assisted_pull_up.webp) | Assisted pull-up machine, Assisted chin and dip | Avustettu leuanveto, Avustettu dippi |
| [shoulder_press.webp](artwork/exercises/shoulder_press.webp) | Shoulder press machine, Seated machine shoulder press | Olkapääprässi, Pystypunnerrus laitteessa |
| [incline_chest_press.webp](artwork/exercises/incline_chest_press.webp) | Incline chest press, Incline press machine | Vinopenkkiprässi |
| [pec_fly_rear_delt.webp](artwork/exercises/pec_fly_rear_delt.webp) | Pec deck, Rear delt fly machine, Pectoral fly machine | Peck deck, Takaolkapäälaite, Rintaperhonen |
| [lateral_raise.webp](artwork/exercises/lateral_raise.webp) | Lateral raise machine, Seated machine lateral raise | Sivuvipulaite, Sivuvipunosto laitteessa |
| [high_row.webp](artwork/exercises/high_row.webp) | High row, Iso-lateral high row | Ylävetosoutu |
| [t_bar_row.webp](artwork/exercises/t_bar_row.webp) | Chest-supported T-bar row, T-bar row machine | Rintatuettu T-tankosoutu |
| [biceps_curl.webp](artwork/exercises/biceps_curl.webp) | Biceps curl machine, Preacher curl machine | Hauiskääntölaite, Hauiskääntö laitteessa |
| [triceps_press.webp](artwork/exercises/triceps_press.webp) | Seated dip machine, Triceps press machine | Ojentajaprässi, Dippiprässi |
| [lat_pullover.webp](artwork/exercises/lat_pullover.webp) | Pullover machine, Plate-loaded pullover | Pullover-laite |
| [leg_extension.webp](artwork/exercises/leg_extension.webp) | Leg extension, Knee extension machine | Reisiojennus, Reiden ojennus |
| [prone_leg_curl.webp](artwork/exercises/prone_leg_curl.webp) | Prone leg curl, Lying leg curl | Reisikoukistus maaten |
| [horizontal_leg_press.webp](artwork/exercises/horizontal_leg_press.webp) | Horizontal leg press, Seated horizontal leg press | Vaakajalkaprässi |
| [hack_squat.webp](artwork/exercises/hack_squat.webp) | Hack squat, Hack squat machine | Hack-kyykky |
| [belt_squat.webp](artwork/exercises/belt_squat.webp) | Belt squat, Belt squat machine | Vyökyykky |
| [hip_thrust.webp](artwork/exercises/hip_thrust.webp) | Hip thrust machine, Glute drive machine | Lantionnostolaite, Lantionnosto laitteessa |
| [hip_abduction_adduction.webp](artwork/exercises/hip_abduction_adduction.webp) | Hip abduction machine, Hip adduction machine, Hip abductor/adductor | Loitonnuslaite, Lähennyslaite, Loitonnus ja lähennys |
| [glute_kickback.webp](artwork/exercises/glute_kickback.webp) | Glute kickback machine, Rear kick machine | Pakarapotkulaite |
| [seated_calf_raise.webp](artwork/exercises/seated_calf_raise.webp) | Seated calf raise, Seated calf machine | Pohjelaite istuen |
| [standing_calf_raise.webp](artwork/exercises/standing_calf_raise.webp) | Standing calf raise machine, Standing calf machine | Pohjelaite seisten |
| [abdominal_crunch.webp](artwork/exercises/abdominal_crunch.webp) | Abdominal crunch machine, Ab crunch machine | Vatsarutistuslaite |
| [back_extension.webp](artwork/exercises/back_extension.webp) | Seated back extension machine, Back extension machine | Selänojennuslaite |
| [torso_rotation.webp](artwork/exercises/torso_rotation.webp) | Torso rotation machine, Rotary torso | Vartalonkiertolaite |
| [dual_adjustable_pulley.webp](artwork/exercises/dual_adjustable_pulley.webp) | Dual adjustable pulley, Cable crossover, Functional trainer | Ristikkäistalja, Säädettävä ristikkäistalja |
| [smith_machine.webp](artwork/exercises/smith_machine.webp) | Smith machine, Smith squat | Smith-laite, Smith-kyykky |
| [power_rack.webp](artwork/exercises/power_rack.webp) | Power rack, Power cage | Voimahäkki, Kyykkyhäkki |
| [treadmill.webp](artwork/exercises/treadmill.webp) | Treadmill, Treadmill run | Juoksumatto |
| [elliptical.webp](artwork/exercises/elliptical.webp) | Elliptical trainer, Elliptical, Cross trainer | Crosstrainer, Ellipsilaite |
| [stair_climber.webp](artwork/exercises/stair_climber.webp) | Stair climber, Stairmill | Porraslaite, Porraskone |
| [rowing_ergometer.webp](artwork/exercises/rowing_ergometer.webp) | Rowing ergometer, Rowing machine, Indoor rower | Soutulaite, Soutuergometri |
| [ski_ergometer.webp](artwork/exercises/ski_ergometer.webp) | Ski ergometer, SkiErg | Hiihtoergometri |
| [recumbent_bike.webp](artwork/exercises/recumbent_bike.webp) | Recumbent bike, Recumbent cycle | Selkänojallinen kuntopyörä, Nojakuntopyörä |
| [indoor_cycle.webp](artwork/exercises/indoor_cycle.webp) | Studio cycle, Indoor cycling bike, Spin bike | Sisäpyöräilypyörä, Spinningpyörä |
| [air_bike.webp](artwork/exercises/air_bike.webp) | Air bike, Fan bike | Ilmanvastuspyörä |
| [band_pulldown.webp](artwork/exercises/band_pulldown.webp) | Band pulldown home, Home band pulldown, Resistance band pulldown | Vastuskuminauhatalja, Ylätalja kuminauhalla |
| [shoulder_mobility.webp](artwork/exercises/shoulder_mobility.webp) | Shoulder mobility, Mobility dowel | Olkapään liikkuvuus, Liikkuvuuskeppi |
| [stretch_mat.webp](artwork/exercises/stretch_mat.webp) | Hip flexor stretch, Stretch mat | Lonkankoukistajan venytys, Venyttelymatto |
| [exercise_equipment.webp](artwork/exercises/exercise_equipment.webp) | Dumbbell and resistance band, Hex dumbbell, Dumbbell | Käsipaino ja vastuskuminauha, Käsipaino |

## 6. Interactive check-in artwork

| Source | Exact use |
| --- | --- |
| [Front body](artwork/checkin/body_front.svg) | Front segment in the existing body-area selector. The viewer’s left side is the person’s right. Chest and abdomen are front torso regions. |
| [Back body](artwork/checkin/body_back.svg) | Back segment of that selector. The viewer’s left side is the person’s left. Upper back and lower back are the corresponding torso regions. |

1. Extract the named region paths from each source once. Apply the SVG group transformation `translate(240 0) scale(-1 1)` to the mirrored half exactly once. Preserve separate decorative paths for head, hair and internal detail, plus their source paint order. The 21 selectable paths on each face comprise neck, two torso regions and nine left/right pairs (shoulder, upper arm, elbow, forearm/wrist, hip, thigh, knee, calf/shin and ankle/foot). Use the original IDs, including underscores. Compare the union of both faces with `bodyRegions`.
2. Draw the figure centered, using `scale = min(availableWidth / 240, availableHeight / 392)` and centered x/y offsets. Convert taps by subtracting **both** offsets and dividing by that scale. Construct the selection overlay and point-in-path hit test from the same transformed paths. Keep the existing nearest-region accommodation for small touch targets, calculate distances in the new coordinate system and resolve exact hits before proximity. A tap clearly outside the figure and its hit accommodation does nothing.
3. Keep the 300 dp normal map container and allow scrolling at large font. The person’s left/right is anatomical in every layout direction. Retain the existing Front/Back controls, phase, selected observations and Choose area menu. The menu must expose the same regions and editing flow to TalkBack and switch users without requiring precise tapping on the drawing.
4. Light mode uses the SVG’s sage/ivory fills and graphite outlines. For dark mode keep the same shapes and set base fills to `#34423D` and `#405048`, ordinary outlines to `#A8B7AB`, detail lines to `#83978B`. Pain uses a solid theme-ink fill; stiffness alone uses an ink outline equivalent to at least 2 dp on screen. When both exist on a region, pain fill wins as it does today. Keep the textual explanation and numerical choices. Never bake the demo’s selected knee into either asset.
5. Check the same anatomical right knee from both sides, a central torso region, side switching and rotation. Record one score, change views, leave and return. The stored observation must still be attached to the original region and phase. Confirm that a front-only region is available again when returning from Back.

[Body drawing and selection owner](../../app/src/main/java/com/emmah/inshape/feature/checkin/BodyMap.kt)

## 7. Music, settings and the widget

### Playlist and track artwork

| Source | Primary placement | Reuse and crop |
| --- | --- | --- |
| [Night Drive square](artwork/music/night_drive_cover.webp) | 68 dp playlist row thumbnail for the Night Drive cover family. | Use the square at compact sizes and in the stacked accessible header. |
| [Night Drive panorama](artwork/music/night_drive.webp) | Wide Night Drive playlist detail header. Put title and song count over the dark start side. | Target aspect ratio 2.4:1, with displayed height between 112 and 180 dp. Keep the lamp and wet street visible at end. Use the square sibling and text below at compact width, large type or in RTL if the bright end collides with text. |
| [Heavy Rotation](artwork/music/heavy_rotation.webp) | Heavy Rotation family playlist row and detail. | Square speaker close-up. Use the full square beside the title at 104 dp in detail. |
| [Slow Motion](artwork/music/slow_motion.webp) | Slow Motion family playlist row and detail. | Square sea-foam cover. Use the same 104 dp detail treatment. |
| [Record grooves](artwork/music/record_grooves.webp) | Generic fallback for a track with no readable art in the 56 dp mini-player and 152 dp Now Playing cover. Also use for an empty playlist. | Keep square, fit inside the existing rounded bounds. A player with no current track retains its existing empty state. |

**Release selection.** For playlists with tracks, use the first readable embedded image from available tracks in playlist order. For tracks use the current track URI. Preserve the existing bounded decoder, URI permission handling, off-main-thread work and cache clearing. Present real personal covers in the current monochrome treatment for this design pass; render the new bundled assets in their authored colors. A user cover of any shape uses the ordinary square/detail layout and native text beside or below. Only the Night Drive *fallback family* uses the dedicated panorama.

**Bundled playlist families.** When a nonempty playlist has no readable cover, compute `Math.floorMod(playlist.id.hashCode(), 3)` against a fixed order of Night Drive, Heavy Rotation and Slow Motion. This gives all three cover families a release path for arbitrary user-created playlists. A rename, track reorder or language change preserves the fallback for the same ID. A newly available personal cover takes priority on the next resolved load. Empty playlist uses grooves until it has tracks. Distinguish an unresolved cover from a confirmed miss so scanning and quick navigation do not flash unrelated family images or leak the previous playlist’s cover.

**Demo routing.** At the debug fixture boundary, explicitly map `night-drive`, `heavy-rotation` and `slow-motion` to these three families. The current fixture tracks use synthetic drawable URIs, which otherwise take precedence over fallback selection. Replace the demo art references with the approved colored squares and explicitly request the Night Drive panorama for its detail fixture. Keep production free of demo title comparisons, debug-only IDs and references to debug resources. Verify the demo thumbnails, real embedded art and a permission-denied URI as three distinct cases.

**Interaction.** Keep Play and Shuffle below the image, readable on the theme surface. Preserve disabled state when no tracks can play, track availability, overflow actions, queue, playback and music chooser behavior. The workout music chooser reuses the same playlist row/detail treatments. Song rows stay text-led. The mini-player shares the current track’s cover and existing actions at its inline and sticky locations.

[Playlist surfaces](../../app/src/main/java/com/emmah/inshape/feature/music/MusicScreens.kt), [cover loader](../../app/src/main/java/com/emmah/inshape/feature/music/MusicArtwork.kt), [player](../../app/src/main/java/com/emmah/inshape/feature/music/PlayerScreens.kt) and [debug covers](../../app/src/debug/java/com/emmah/inshape/demo/DemoMusic.kt)

### Settings appearance

Replace only the Appearance → Theme selector presentation with three labeled preview choices. Map [System](artwork/settings/theme_system.svg), [Light](artwork/settings/theme_light.svg) and [Dark](artwork/settings/theme_dark.svg) explicitly to the matching `ThemeMode` entries. The System art is a fixed split preview, even while the operating system changes appearance. Show the selection from the observed setting and write through the existing `themeSettings.set` callback.

At regular width use three equal columns with 8 dp gaps. Preserve the 160:124 artwork ratio, cap each preview at 112 dp wide and put its label below. Use a 2 dp ink outline and an accessible selected state on the whole choice. The miniature controls inside the illustration are decorative. At compact width or large type use three full-width labeled rows with 48 dp thumbnails and a single radio-style selection per row. Use one focus stop per choice and retain the selected state after rotation, relaunch and changing the system setting. Keep the other Settings groups on their ordinary surfaces.

[Settings owner](../../app/src/main/java/com/emmah/inshape/feature/settings/SettingsScreen.kt)

### Home-screen widget

Use the [resistance band](artwork/widget/resistance_band.webp) only in the wide, idle home widget and the corresponding wide picker preview. At the 250 × 130 dp breakpoint place it at the trailing edge in an approximately 68 × 56 dp slot. Keep the count across the top and keep the Start action in its existing click area. If the resolved font size, launcher padding or translated label leaves insufficient space, omit the band. The 160 × 130 dp widget, the active-workout widget and the review-timing widget prioritize the title, count, action and native chronometer.

Implement the layer using a Glance-compatible packaged drawable/background and resource-backed image provider. Match the routine wash with day/night Android shape resources (start, center and end colors from section 3, center at 0.48). Use the existing rounded widget bounds. On a launcher/API combination that cannot render the drawable as intended, use the existing day/night solid fill with the same wide idle band. Match the generated picker preview through `providePreview`, and inspect the widget provider preview resource on older hosts. Preserve native chronometer updates, the original start/resume intents, host resizing and existing theme providers. Activation must remove the idle illustration on the next normal widget update.

[Widget owner](../../app/src/main/java/com/emmah/inshape/widget/InShapeWidget.kt) and [Glance layout guidance](https://developer.android.com/develop/ui/compose/glance/build-ui)

## 8. Implementation order and verification gates

| Order | Work package | Evidence required before moving on |
| ---: | --- | --- |
| 1 | Import the 57 bitmap resources and convert the seven SVG sources. Add the color stops alongside the existing theme tokens. Preserve shared card defaults for all existing callers. | Resource linking succeeds. Compare transparency on light and dark backgrounds and the vector renders at target size. Verify that the final application resource graph reaches all 64 source designs. |
| 2 | Implement catalog lookup and routine selection. Wire the library row, shared picker, personal-photo panel and routine summary. | Parameterized lookup covers the 44 rows, all ten original demo exercise names, precedence, spelling normalization, archive/restore and a missing match. Verify a user photo, replacement, removal and reload failure on an emulator. |
| 3 | Apply Today, workout, finish, calendar and progress treatments in that order. Keep state branching in the existing screen owners. | Verify the three routine types, the empty start, inverted active card, unknown timing, scrolling clock, editable sets, note-only and empty finish, selected calendar day and empty/populated attendance. |
| 4 | Replace check-in art and its geometry in one change. Implement the accessible theme previews. | Point-in-path tests and end-to-end region selection agree on both faces. All 23 distinct region IDs are reachable across the two faces. System, Light and Dark each remain selectable with touch and TalkBack. |
| 5 | Wire music fallback families, debug fixtures and the real-art precedence. Implement the Glance wide idle illustration. | A release-style custom playlist reaches each fallback, all three demo covers render as directed, personal cover priority works, and the real launcher shows both live widget states and sizes. |
| 6 | Run the responsive and regression checks below. Record actual results and any remaining gaps in the implementation handoff. | Every source image has a verified route or state. Compare against original screen captures and current functional behavior. |

### Validation matrix for the implementation

| Setting or scenario | Inspect | Pass condition |
| --- | --- | --- |
| 390 dp width, 100% font, Light and Dark | All 14 approved concept surfaces. Exercise the regular artwork slots, calendar summary, populated and empty progress, two playlist layouts and system-themed appearance selector. | Expect the focal image in its specified slot. Text and actions retain hierarchy. No opaque cutout rectangle, clipped handle, frame around a photo blend or image under chart data. |
| **320 dp width, 100% font** | Today suggestion and active card, all library slots, routine summary, workout and finish, calendar totals, check-in, settings, music header and chooser. | Compact rules apply using actual inner width. Tap targets, names and counts remain usable. |
| **390 dp width, 200% font** | The same flows in both themes. Include long user text, missing timing and duration longer than one hour. | Text reflows, art yields space, Continue/Start/Save and playback controls remain reachable. |
| **320 dp width and 200% font together** | Repeat each affected flow in Light and Dark. Include IME open while logging sets, a two-line exercise name, a long playlist name and a selected body area. Scroll to the last action. | No overlap, clipped controls or horizontal scrolling. The correct selected state survives rotation. Verify the choice menu as an alternative to tapping the body. |
| Wide/tablet and landscape | One example per layout family at the existing maximum content width; workout with mini-player on a short landscape display; one RTL layout. | Images stop growing at their caps. Reading order follows layout direction. The right knee and left knee keep their anatomical meaning. |
| Accessibility | Traverse at least Today → workout, Library → exercise, both body faces, theme selector, playlist → playback and widget with TalkBack. Look at text and essential outlines against the rendered background. | Decorative images add no extra announcements. Personal photo actions remain discoverable. Theme choices announce label and selection. Each body region is selectable by name. Record actual contrast values for the worst representative crops. |
| Equipment coverage and photo integrity | Exercise all 44 catalog keys through real UI input or a debug-only set of test records. Inspect every thumbnail in a contact sheet, then open one from each equipment family, an ambiguous pair and the four accessory illustrations. | Resource mapping test is exhaustive. Inspect particularly upright/recumbent/spin/air bike, 45°/horizontal leg press, seated/prone curl, standing/seated calf and Smith machine/power rack. Observe fallback after deleting the last personal photo. |
| Personal data and offline music | Open an existing journal, restore a journal with photos, rename/reorder a custom playlist, deny/revoke music folder access, rescan, play and pause from workout. | User files, captured setup, exercise comparisons and recorded values survive. Missing art does not interrupt playback or obscure recovery. UI loads bundled artwork offline. |
| Real launcher | Place and resize a widget across 160 and 250 dp in both host themes. Inspect live and picker views; start, resume, switch to unknown timing and finish a workout. | Start/Resume opens the correct destination. The elapsed clock still advances through its native chronometer. Narrow and active layouts give their full space to workout information. |

### Checks and acceptance record

Use the project wrapper from the repository root. Build the debug app and its test APK, run relevant JVM tests and lint, then exercise instrumented flows on an isolated emulator. These are future implementation checks.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Extend the closest existing tests where the requested behavior changes. Use `SuggestedRoutineTest` to protect the selection of the actual routine and a focused presentation test for its picture. Use `BodyMapTest`, `ReferenceLayoutTest`, `RedesignInteractionTest`, `ProgressHistoryCoverageTest`, `MusicUiTest` and `LauncherWidgetTest` for their respective flows. Update a visual expectation only when the approved placement explains the difference. Use existing fixtures including `ExerciseWithSetup`, `LibraryRoutineExpanded`, `ActiveWorkoutPartiallyDone`, `FinishSummary`, `CheckInAfter`, `HistoryNovember`, `ProgressWithHistory`, `SettingsConfigured`, `MusicHome`, `MusicPlaylist` and `WorkoutWithMusic`. Select a period with no qualifying workout to verify the blank notebook independently of sparse history.

For handoff, record the build and device, actual width and font scale, theme, tested state, result and a link to the relevant capture. Keep one capture per distinct visual decision or defect. Report emulator checks, physical-device checks and TalkBack speech separately. Completion requires matching the 64-file inventory to the release representations, visual inspection at actual display sizes and successful interaction checks for the affected surfaces.

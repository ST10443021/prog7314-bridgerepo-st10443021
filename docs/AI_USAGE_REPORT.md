# AI Usage Sub-Report — Part 2

**Module:** PROG7314 / OPSC7312 — POE Part 2 (App Prototype Development)
**Student Number:** ST10443021

Generative AI (Anthropic's Claude) was used as a code-generation and drafting assistant while building this prototype, under my direction and review throughout. Its use is disclosed here in full, per the Part 2 brief's requirement to detail "the exact ways generative AI frameworks were used to debug errors, structure files, or optimise your prototype code" (Anthropic, 2026).

**Code structuring and generation:** I directed the overall architecture (offline-first MVVM on Android, a matching ASP.NET Core REST API) based directly on my own Part 1 Planning and Design document, and Claude generated the corresponding Kotlin/Jetpack Compose and C#/ASP.NET Core source files from that direction — entities, DAOs, repositories, ViewModels, Compose screens, API controllers, and Gradle/MSBuild project files.

**Pixel-accurate UI translation:** I supplied the Part 1 Figma Make pixel-accurate design export. Claude extracted the exact colour tokens, typography and layout structure from that export's `index.css` and component files and translated them into native Jetpack Compose theme and screen code, so the Android UI matches the approved design rather than being freshly reinterpreted.

**Debugging during generation:** while assembling the codebase, Claude caught and corrected several of its own mistakes before delivery — for example, an incorrect standalone-function call pattern for Compose `Modifier` extensions (`clip`, `clickable`) that would have failed to compile, and a media-type extension import error in the Retrofit network module. These were found through static review (import cross-referencing, brace/paren balance checking, and a full scan cross-matching every `R.string`/`R.drawable`/`R.mipmap` reference in the Kotlin source against the actual resource files) rather than by compiling the project, because no Android SDK or .NET SDK was available in the environment used to assemble this submission.

**Unit test generation:** Claude drafted the JUnit/MockK/Turbine tests for the Android app and the xUnit tests for the API, covering the currency-conversion logic, the offline-first repository behaviour, the authentication view-model state machine, and password-hashing/JWT-issuing correctness, based on the business rules I specified.

**What was not AI-generated:** the underlying app concept, feature set, REST API design, data model, and UI layout all originate from my own Part 1 Research Report and Planning and Design document, produced before this Part 2 submission. I reviewed every generated file, understand the architecture it implements, and take full responsibility for the final codebase, including running and fixing it ahead of the demonstration video as noted in the README's "Known limitations" section.

**Important caveat for transparency:** because this codebase was assembled without access to Android Studio, the Android SDK, or the .NET SDK, it has not actually been compiled or run. I verified it as thoroughly as static review allows, but I take responsibility for building it, resolving any first-run issues, and confirming all functionality on a real device before recording the required demonstration video.

This report is under the 500-word limit set by the Part 2 brief.

## Reference List

Anthropic, 2026. *Claude (Sonnet 5)* [Large language model]. [Online] Available at: https://claude.ai [Accessed 1 August 2026].

# Milestone 1 JavaFX starter

## Open and run in IntelliJ IDEA
1. Unzip/extract the ZIP first (Windows: right-click → Extract All; macOS: double-click). Find the extracted folder directly containing `pom.xml`, `README.md`, `.gitignore`, and `src/`. If there is an extra outer folder, open it to find this project folder.
2. In IntelliJ choose File → Open and select that folder. Do not create a new project.
3. Trust the project if prompted and allow Maven to load dependencies (internet required on first run).
4. Select JDK 21 as the project SDK and Maven runner JRE. IntelliJ bundles Maven; a separate Maven install is unnecessary for this IDE workflow.
5. Open View → Tool Windows → Maven. If the project is missing, use Add Maven Projects and select `pom.xml`.
6. In the Maven tool window choose Execute Maven Goal and enter `clean javafx:run`.
7. Expect a window titled “CSSE220 Team Starter”. Click “Tap to update status”; the message should change to “Your team has launched the shared starter.” Close the window to stop the app.

Use the Maven goal as the shared launch method. Running StarterApp directly can require extra JavaFX runtime configuration.

## Team setup
**TEAM LEAD ONLY:** download/extract the starter, run it, and initialize the shared project. In GitHub Desktop choose **File → New Repository**. Enter Name `Milestone1Starter` and Local Path equal to its parent `CSSE220FinalProject` folder. The combined path must point to the existing folder containing `pom.xml`, not a nested `Milestone1Starter/Milestone1Starter` folder.

Leave “Initialize this repository with a README” unchecked, Git ignore set to **None**, and License set to **None**. We already supply README.md and `.gitignore`; None means no additional ignore template is generated. Click Create Repository, check Repository → Show in Finder/Explorer opens the folder containing `pom.xml` and `src/`, then publish and invite teammates.

**OTHER TEAMMATES:** do not create a repository or initialize Git. Wait for the lead’s invitation and team URL, accept in your browser, then use **GitHub Desktop → File → Clone repository**. Set the clone dialog’s Local path to a new full destination, such as `/Users/YourName/CSSE220FinalProject/TeamProject` or `C:\Users\YourName\CSSE220FinalProject\TeamProject`, outside cloud-sync folders and other repositories. The final folder name should appear once; do not clone into the parent folder or an extracted starter. After cloning, use Desktop → Repository → Show in Finder/Explorer to check that `pom.xml` and `src/` are directly inside the clone. Open that exact folder in IntelliJ. The lead keeps the original local repository and does not clone again.

Everyone keeps the project outside OneDrive, iCloud Drive, Dropbox, or Google Drive, for example in `/Users/YourName/CSSE220FinalProject` or `C:\Users\YourName\CSSE220FinalProject`. Desktop/Documents may be cloud-synced; check the full path. Create/switch branches in **GitHub Desktop → Current Branch → New Branch**; edit files in IntelliJ.

Follow the [full GitHub Desktop guide](https://obscrivn.github.io/csse220web/github-desktop-guide.html) for the exact sequence and checkpoints.

## Files
- `src/main/java/finalproject/StarterApp.java`: working minimal JavaFX app.
- `pom.xml`: Java 21, JavaFX controls, and Maven launch configuration.
- `.gitignore`: excludes build output, IDE settings, and OS files.

The TODO comments suggest future customization; they do not mean the baseline app is broken. First confirm the unchanged app runs. For the initial Git exercise, create separate documentation files so teammates do not all edit StarterApp.java.

## Troubleshooting
- “release version 21 not supported”: check both project SDK and Maven runner JRE.
- Dependencies unavailable: allow Maven to finish downloading and reload Maven; share the exact error with the instructor if it persists.
- JavaFX runtime components missing: launch with Maven `clean javafx:run` rather than the class run button.
- No visible window: check whether the process is still starting and whether the window is behind the IDE; record any error in the Run console.

Terminal alternative, only with Maven already installed: `mvn clean javafx:run` from the folder containing `pom.xml`.

💧 Dauin Integrated Water Meter Reader
A professional, high-performance mobile solution designed for the Municipality of Dauin. This application streamlines the utility management process by replacing manual entry with high-accuracy OCR technology and real-time data synchronization.

🚀 Key Features

1.   Smart OCR Scanning: Powered by Google ML Kit / Tesseract for on-device digit recognition ($0-9$) with a $90\%$ confidence threshold to ensure billing accuracy.

2.   Photo Verification: Captures and attaches a timestamped photo of the meter reading for audit trails and dispute resolution.

3. Offline Mode & Geo-Tagging: Technicians can record readings in areas with poor connectivity; data syncs automatically once a signal is restored, including GPS coordinates for location verification.

4. Route Optimization: Intelligent mapping to help readers navigate their assigned zones efficiently.

5. Consumer Transparency: Integrates with a consumer-facing billing interface for instant access to consumption history.


🛠️ Tech Stack

1. Mobile Framework: Android SDK / Flutter (as applicable)

2. OCR Engine: Google ML Kit / Tesseract OCR

3. Backend & Database: Firebase (Firestore, Authentication, and Cloud Storage)

4. Image Processing: Grayscale conversion and binarization for enhanced scanning accuracy.



   **🛠️ Field Operations: User Guide**

   
Follow these steps to ensure accurate data collection and seamless synchronization with the Municipality of Dauin billing system.

1. 🚦 Preparation
Check Connectivity: Ensure your mobile data or Wi-Fi is on before leaving the office to sync your assigned route.

Battery Check: Verify your device has at least 20% battery or a power bank is available, as the OCR camera and GPS use significant power.

Clean the Lens: Briefly wipe your camera lens to ensure the OCR sensor can clearly "see" the meter digits.

2. 🔍 Scanning a Meter
Open the App: Select your assigned zone or route from the main dashboard.

Locate Account: Search for the consumer by name or Account ID, or select the next closest house on your map.

Capture Reading:

Tap the "Scan Meter" button to open the viewfinder.

Center the water meter's mechanical digits within the scanning box.

Wait for the Confidence Indicator to turn green (minimum 90% accuracy).

The app will automatically capture a timestamped, geo-tagged photo for verification.

3. 📝 Verification & Entry
Manual Check: Always double-check the OCR-detected number against the physical meter.

Correction: If the meter face is dirty or scratched and the OCR fails, use the "Manual Entry" option and take a clear photo of the dial as proof.

Notes: If a leak is spotted or a seal is broken, use the "Report Issue" flag to notify the maintenance team immediately.

4. 🛰️ Offline Usage & Syncing
No Signal? Don’t worry. The app will save the reading, photo, and GPS coordinates locally on your device.

Syncing: Once you return to an area with a stable connection (or the municipal office), tap the "Sync All" button.

Confirmation: Do not log out or clear your cache until you see the "All Records Uploaded" confirmation message.

Pro-Tip: For the best OCR results, hold the phone parallel to the meter face and avoid direct sunlight glare by shading the meter with your hand if necessary.

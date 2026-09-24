(ns app.template.frontend.i18n.mobile)

(def dicts
  {:bs
   {;; Mobile tab labels
    :mobile/tab-dashboard "Početna"
    :mobile/tab-expenses  "Troškovi"
    :mobile/tab-reports   "Izvještaji"
    :mobile/tab-more      "Više"
    :mobile/tab-upload    "Učitaj"

    ;; Mobile upload page
    :mobile/capture-receipt    "Fotografiraj račun"
    :mobile/take-photo         "Slikaj"
    :mobile/take-photo-sub     "Koristi kameru za snimanje računa"
    :mobile/from-gallery       "Odaberi iz galerije"
    :mobile/from-gallery-sub   "Odaberi postojeću fotografiju"
    :mobile/manual-entry       "Ručni unos"
    :mobile/add-manually       "Dodaj trošak ručno"
    :mobile/add-manually-sub   "Unesi podatke bez računa"
    :mobile/add-utility        "Dodaj režiju"
    :mobile/add-utility-sub    "Struja, voda, plin ili grijanje"
    :mobile/pending-reviews    "Na čekanju"
    :mobile/pending-reviews-desc "Računi obrađeni OCR-om, čekaju vašu potvrdu."
    :mobile/review-now         "Pregledaj"
    :mobile/receipts-queued    "računa čeka sinkronizaciju"
    :mobile/sync-now           "Sinkroniziraj"

    ;; Mobile manual entry form
    :mobile/search-supplier    "Traži dobavljača..."
    :mobile/search-store       "Traži prodavnicu..."
    :mobile/search-payer       "Traži kupca..."
    :mobile/search-category    "Traži kategoriju..."
    :mobile/notes-placeholder  "Opcionalne napomene..."

    ;; Mobile receipt review
    :mobile/review-receipt     "Pregledaj račun"
    :mobile/confirm-approve    "Potvrdi i spremi"
    :mobile/unknown-supplier   "Nepoznat dobavljač"
    :mobile/unknown-store      "Nepoznata trgovina"

    ;; Mobile dashboard
    :mobile/monthly-trend      "Mjesečni trend"
    :mobile/categories         "Kategorije"
    :mobile/total-30d          "Zadnjih 30 dana"
    :mobile/expense-count      "Troškovi"
    :mobile/vs-prev            "u odnosu na prethodno"
    :mobile/daily-avg          "Dnevni prosjek"
    :mobile/weekly-avg         "Sedmični prosjek"
    :mobile/monthly-avg        "Mjesečni prosjek"
    :mobile/top-suppliers      "Top dobavljači"
    :mobile/biggest-expense    "Najveći trošak"

    ;; Mobile expense list
    :mobile/search-expenses    "Traži po dobavljaču..."
    :mobile/no-expenses        "Nema pronađenih troškova"
    :mobile/load-more          "Učitaj više"
    :mobile/expense-detail     "Detalji troška"

    ;; Mobile receipts
    :mobile/receipts-title     "Računi"
    :mobile/view-receipts      "Pregledaj sve učitane račune"
    :mobile/no-receipts        "Nema pronađenih računa"

    ;; Mobile reports
    :mobile/by-category        "Po kategoriji artikla"
    :mobile/monthly-spending   "Mjesečna potrošnja"
    :mobile/by-day             "Po danu u sedmici"
    :mobile/uncategorized      "Nekategorizirano"

    ;; Mobile more menu
    :mobile/search             "Pretraga"
    :mobile/language            "Jezik"
    :mobile/change-on-desktop  "Promijenite jezik na desktopu"

    ;; Mobile login
    :mobile/app-subtitle       "Bookkeeping Mobile"
    :mobile/no-account         "Nemate račun?"
    :mobile/signup-desktop     "Registrujte se na desktopu."

    ;; Mobile forgot password
    :mobile/back-to-login      "Nazad na prijavu"

    ;; Mobile manual entry / line items
    :mobile/item-label         (fn [n] (str "Stavka " n))
    :mobile/remove-item        "Ukloni"
    :mobile/item-name-placeholder "Naziv stavke"
    :mobile/qty-placeholder    "Kol."
    :mobile/price-placeholder  "Cijena"
    :mobile/total-placeholder  "Ukupno"
    :mobile/add-item           "+ Dodaj stavku"

    ;; Mobile receipt statuses
    :mobile/status-uploaded    "Učitano"
    :mobile/status-processing  "Obrađuje se"
    :mobile/status-extracting  "Ekstrahuje"
    :mobile/status-ready       "Spremno"
    :mobile/status-review      "Pregled"
    :mobile/status-approved    "Odobreno"
    :mobile/status-posted      "Proknjiženo"
    :mobile/status-failed      "Neuspjelo"

    ;; Mobile day names
    :mobile/day-mon            "Pon"
    :mobile/day-tue            "Uto"
    :mobile/day-wed            "Sri"
    :mobile/day-thu            "Čet"
    :mobile/day-fri            "Pet"
    :mobile/day-sat            "Sub"
    :mobile/day-sun            "Ned"

    ;; Mobile camera UI
    :mobile/flash-on           "Blic uklj."
    :mobile/flash-off          "Blic isklj."
    :mobile/iphone-flash-on    "iPhone blic uklj."
    :mobile/iphone-flash-off   "iPhone blic isklj."
    :mobile/iphone-flash       "iPhone blic"
    :mobile/use-iphone-flash   "Koristi iPhone blic"
    :mobile/back               "Nazad"
    :mobile/torch-on           "Lampa uklj."
    :mobile/torch-off          "Lampa isklj."
    :mobile/opening-camera     "Otvaranje kamere..."
    :mobile/status-capturing   "Snimam"
    :mobile/status-review-capture "Pregled snimka"
    :mobile/status-device-camera "Kamera uređaja"
    :mobile/status-flash-ready "Blic spreman"
    :mobile/status-camera-ready "Kamera spremna"
    :mobile/receipt-captured-label "Račun snimljen"
    :mobile/take-another-or-exit "Slikaj još jedan račun ili izađi?"
    :mobile/save-native-help   "Spremi sliku, zatim koristi Snimi ponovo za iPhone kameru, ili spremi i vrati se na Učitavanje."
    :mobile/save-live-help     "Spremi sliku i nastavi snimati, ili spremi i vrati se na Učitavanje."
    :mobile/iphone-flash-hint  "iPhone blic ostaje u nativnoj kameri. Nakon spremanja, tapni Snimi ponovo za sljedeći račun."
    :mobile/use-and-take-another "Spremi i slikaj još"
    :mobile/use-and-exit       "Spremi i izađi"
    :mobile/retake             "Ponovo slikaj"
    :mobile/uploading          "Učitavam..."
    :mobile/lens-label         "Objektiv"
    :mobile/preview-label      "Pregled"
    :mobile/captured-fallback-msg "Račun snimljen. Koristite opcije ispod za nastavak."
    :mobile/native-flash-prompt "Tapni Snimi ispod za otvaranje iPhone kamere s blicom."
    :mobile/native-camera-prompt "Preglednik ne može držati pregled otvorenim. Koristite Snimi ispod za kameru uređaja."

    ;; Mobile camera errors
    :mobile/camera-err-not-allowed "Pristup kameri blokiran. Možete koristiti kameru uređaja."
    :mobile/camera-err-not-found "Zadnja kamera nije pronađena na ovom uređaju."
    :mobile/camera-err-not-readable "Kamera je u upotrebi od strane druge aplikacije."
    :mobile/camera-err-overconstrained "Ovaj uređaj ne može otvoriti preferiranu zadnju kameru."
    :mobile/camera-err-security "Kamera zahtijeva siguran kontekst pretraživača."
    :mobile/camera-err-abort   "Kamera je prekinuta prije završetka otvaranja."
    :mobile/camera-err-default "Pokretanje kamere neuspješno. Možete koristiti kameru uređaja."
    :mobile/camera-unavailable "Pregled kamere nije dostupan. Snimanje koristi kameru uređaja."
    :mobile/torch-unavailable  "Kontrola lampe nije dostupna na ovom uređaju/pretraživaču."
    :mobile/flash-native-hint  "Pokušat ćemo okinuti web fotografiju s blicom, ali neki iPhone/Safari pregledi mogu zanemariti blic. Ako je račun i dalje taman, uključite lampu ili koristite kameru uređaja."
    :mobile/preview-not-ready  "Pregled kamere nije spreman. Pokušajte ponovo za sekundu."
    :mobile/capture-failed     "Snimanje fotografije neuspješno."

    ;; Mobile toast messages
    :mobile/toast-expense-created "Trošak kreiran"
    :mobile/toast-utility-created "Račun za režije kreiran"
    :mobile/toast-receipt-uploaded "Račun uspješno učitan"

    ;; Mobile quick-add workflow
    :mobile/quick-add-title    "Brzi unos"
    :mobile/continue           "Nastavi"
    :mobile/no-results         "Nema rezultata"
    :mobile/items-title        "Stavke"
    :mobile/add-article        "+ Artikal"
    :mobile/total-label        "Ukupno"
    :mobile/suggested-context  "Predloženi kontekst"
    :mobile/phase2-title       "Detalji troška"
    :mobile/back-to-items      "Nazad na stavke"}

   :en
   {;; Mobile tab labels
    :mobile/tab-dashboard "Home"
    :mobile/tab-expenses  "Expenses"
    :mobile/tab-reports   "Reports"
    :mobile/tab-more      "More"
    :mobile/tab-upload    "Upload"

    ;; Mobile upload page
    :mobile/capture-receipt    "Capture Receipt"
    :mobile/take-photo         "Take Photo"
    :mobile/take-photo-sub     "Use camera to capture receipt"
    :mobile/from-gallery       "Choose from Gallery"
    :mobile/from-gallery-sub   "Select existing photo"
    :mobile/manual-entry       "Manual Entry"
    :mobile/add-manually       "Add Expense Manually"
    :mobile/add-manually-sub   "Enter details without receipt"
    :mobile/add-utility        "Add utility bill"
    :mobile/add-utility-sub    "Electricity, water, gas or heating"
    :mobile/pending-reviews    "Pending Reviews"
    :mobile/pending-reviews-desc "Receipts processed by OCR, awaiting your confirmation."
    :mobile/review-now         "Review Now"
    :mobile/receipts-queued    "receipts queued offline"
    :mobile/sync-now           "Sync Now"

    ;; Mobile manual entry form
    :mobile/search-supplier    "Search supplier..."
    :mobile/search-store       "Search store..."
    :mobile/search-payer       "Search payer..."
    :mobile/search-category    "Search category..."
    :mobile/notes-placeholder  "Optional notes..."

    ;; Mobile receipt review
    :mobile/review-receipt     "Review Receipt"
    :mobile/confirm-approve    "Confirm & Save"
    :mobile/unknown-supplier   "Unknown supplier"
    :mobile/unknown-store      "Unknown store"

    ;; Mobile dashboard
    :mobile/monthly-trend      "Monthly Trend"
    :mobile/categories         "Categories"
    :mobile/total-30d          "Last 30 days"
    :mobile/expense-count      "Expenses"
    :mobile/vs-prev            "vs previous"
    :mobile/daily-avg          "Daily avg"
    :mobile/weekly-avg         "Weekly avg"
    :mobile/monthly-avg        "Monthly avg"
    :mobile/top-suppliers      "Top Suppliers"
    :mobile/biggest-expense    "Biggest Expense"

    ;; Mobile expense list
    :mobile/search-expenses    "Search by supplier..."
    :mobile/no-expenses        "No expenses found"
    :mobile/load-more          "Load more"
    :mobile/expense-detail     "Expense Detail"

    ;; Mobile receipts
    :mobile/receipts-title     "Receipts"
    :mobile/view-receipts      "View all uploaded receipts"
    :mobile/no-receipts        "No receipts found"

    ;; Mobile reports
    :mobile/by-category        "By Article Category"
    :mobile/monthly-spending   "Monthly Spending"
    :mobile/by-day             "By Day of Week"
    :mobile/uncategorized      "Uncategorized"

    ;; Mobile more menu
    :mobile/search             "Search"
    :mobile/language           "Language"
    :mobile/change-on-desktop  "Change language on desktop"

    ;; Mobile login
    :mobile/app-subtitle       "Bookkeeping Mobile"
    :mobile/no-account         "Don't have an account?"
    :mobile/signup-desktop     "Sign up on desktop."

    ;; Mobile forgot password
    :mobile/back-to-login      "Back to login"

    ;; Mobile manual entry / line items
    :mobile/item-label         (fn [n] (str "Item " n))
    :mobile/remove-item        "Remove"
    :mobile/item-name-placeholder "Item name"
    :mobile/qty-placeholder    "Qty"
    :mobile/price-placeholder  "Price"
    :mobile/total-placeholder  "Total"
    :mobile/add-item           "+ Add Item"

    ;; Mobile receipt statuses
    :mobile/status-uploaded    "Uploaded"
    :mobile/status-processing  "Processing"
    :mobile/status-extracting  "Extracting"
    :mobile/status-ready       "Ready"
    :mobile/status-review      "Review"
    :mobile/status-approved    "Approved"
    :mobile/status-posted      "Posted"
    :mobile/status-failed      "Failed"

    ;; Mobile day names
    :mobile/day-mon            "Mon"
    :mobile/day-tue            "Tue"
    :mobile/day-wed            "Wed"
    :mobile/day-thu            "Thu"
    :mobile/day-fri            "Fri"
    :mobile/day-sat            "Sat"
    :mobile/day-sun            "Sun"

    ;; Mobile camera UI
    :mobile/flash-on           "Flash On"
    :mobile/flash-off          "Flash Off"
    :mobile/iphone-flash-on    "iPhone Flash On"
    :mobile/iphone-flash-off   "iPhone Flash Off"
    :mobile/iphone-flash       "iPhone Flash"
    :mobile/use-iphone-flash   "Use iPhone Flash"
    :mobile/back               "Back"
    :mobile/torch-on           "Torch On"
    :mobile/torch-off          "Torch Off"
    :mobile/opening-camera     "Opening camera..."
    :mobile/status-capturing   "Capturing"
    :mobile/status-review-capture "Review Capture"
    :mobile/status-device-camera "Device Camera"
    :mobile/status-flash-ready "Flash Ready"
    :mobile/status-camera-ready "Camera Ready"
    :mobile/receipt-captured-label "Receipt Captured"
    :mobile/take-another-or-exit "Take another receipt or exit?"
    :mobile/save-native-help   "Save this image, then use Capture again to open the iPhone camera, or save it and go back to Upload."
    :mobile/save-live-help     "Save this image and keep shooting here, or save it and go back to Upload."
    :mobile/iphone-flash-hint  "iPhone camera flash stays inside the native camera. After saving, tap Capture again to open it for the next receipt."
    :mobile/use-and-take-another "Use & Take Another"
    :mobile/use-and-exit       "Use & Exit"
    :mobile/retake             "Retake"
    :mobile/uploading          "Uploading..."
    :mobile/lens-label         "Lens"
    :mobile/preview-label      "Preview"
    :mobile/captured-fallback-msg "Receipt captured. Use the actions below to keep it, take another, or exit."
    :mobile/native-flash-prompt "Tap Capture below to open the iPhone camera with flash controls."
    :mobile/native-camera-prompt "This browser cannot keep the live preview open here. Use Capture below to open the device camera."

    ;; Mobile camera errors
    :mobile/camera-err-not-allowed "Camera access was blocked. You can still use the device camera instead."
    :mobile/camera-err-not-found "No rear camera was found on this device."
    :mobile/camera-err-not-readable "The camera is already in use by another app."
    :mobile/camera-err-overconstrained "This device could not open the preferred rear camera."
    :mobile/camera-err-security "The in-app camera needs a secure browser context on this device."
    :mobile/camera-err-abort   "The camera was interrupted before it finished opening."
    :mobile/camera-err-default "Couldn't start the in-app camera. You can still use the device camera instead."
    :mobile/camera-unavailable "Live camera preview is unavailable here. Capture will use the device camera instead."
    :mobile/torch-unavailable  "Torch control is not available on this device/browser."
    :mobile/flash-native-hint  "We'll try taking a web photo with flash here, but some iPhone/Safari builds may ignore it. If the receipt is still dark, switch on the torch or use the device camera."
    :mobile/preview-not-ready  "The camera preview is not ready yet. Try again in a second."
    :mobile/capture-failed     "Couldn't capture a photo from the live camera."

    ;; Mobile toast messages
    :mobile/toast-expense-created "Expense created"
    :mobile/toast-utility-created "Utility bill created"
    :mobile/toast-receipt-uploaded "Receipt uploaded successfully"

    ;; Mobile quick-add workflow
    :mobile/quick-add-title    "Quick Add"
    :mobile/continue           "Continue"
    :mobile/no-results         "No results"
    :mobile/items-title        "Items"
    :mobile/add-article        "+ Article"
    :mobile/total-label        "Total"
    :mobile/suggested-context  "Suggested context"
    :mobile/phase2-title       "Expense Details"
    :mobile/back-to-items      "Back to items"}})

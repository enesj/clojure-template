(ns app.template.frontend.i18n.entities)

(def dicts
  {:bs
   {;; Suppliers page
    :suppliers/title                      "Dobavljači"
    :suppliers/subtitle                   "Zajednički katalog dobavljača za vaše domaćinstvo"
    :suppliers/btn-dashboard              "Nadzorna ploča"
    :suppliers/read-only-notice           "Pristup samo za čitanje. Zamolite člana domaćinstva da ažurira dobavljače."
    :suppliers/detail-title               "Detalji dobavljača"
    :suppliers/not-found                  "Dobavljač nije pronađen."
    :suppliers/col-name                   "Ime"
    :suppliers/col-normalized-key         "Normalizovani ključ"
    :suppliers/col-address                "Adresa"
    :suppliers/col-created                "Kreirano"
    :suppliers/col-id                     "ID"
    :suppliers/recent-expenses-title      "Nedavni troškovi"
    :suppliers/recent-expenses-empty      "Još nema troškova za ovog dobavljača."
    :suppliers/aliases-title              "Pravila artikala"
    :suppliers/aliases-empty              "Nema pravila artikala za ovog dobavljača."
    :suppliers/col-purchased              "Kupljeno"
    :suppliers/col-total                  "Ukupno"
    :suppliers/col-payer                  "Kupac"
    :suppliers/col-status                 "Status"
    :suppliers/col-raw-label              "Originalna oznaka"
    :suppliers/col-article                "Artikal"
    :suppliers/col-normalized             "Normalizovano"
    :suppliers/delete-title               "Obriši dobavljača"
    :suppliers/delete-msg                 "Želite li obrisati ovog dobavljača?"

    ;; Payers page
    :payers/title                         "Kupci"
    :payers/subtitle                      "Zajednički načini plaćanja za vaše domaćinstvo"
    :payers/btn-dashboard                 "Nadzorna ploča"
    :payers/read-only-notice              "Pristup samo za čitanje. Zamolite člana domaćinstva da ažurira kupce."
    :payers/delete-title                  "Obriši kupca"
    :payers/delete-msg                    "Želite li obrisati ovog kupca?"
    :payers/delete-disabled-linked        "Ovaj kupac se već koristi na postojećim troškovima. Deaktivirajte ga kroz Izmijeni umjesto brisanja."
    :payers/delete-disabled-generic       "Ovaj kupac trenutno ne može biti obrisan."
    :payers/form-add-title                "Dodaj kupca"
    :payers/form-edit-title               "Izmijeni kupca"
    :payers/form-created-success          "Kupac je uspješno dodan."
    :payers/form-updated-success          "Kupac je uspješno ažuriran."
    :payers/form-placeholder              "npr. Zajednički novčanik"
    :payers/form-own-placeholder          "npr. Visa 1234"
    :payers/type-system                   "Sistemski"
    :payers/type-custom                   "Prilagođeni"

    ;; Articles page
    :articles/title                       "Artikli"
    :articles/subtitle                    "Katalog artikala za napredne korisnike (koristi se za mapiranje i pravila)"
    :articles/btn-dashboard               "Nadzorna ploča"
    :articles/btn-unmapped                "Neizmapirana pravila"
    :articles/delete-title                "Obriši artikal"
    :articles/delete-msg                  "Želite li obrisati ovaj artikal?"

    ;; Categories page
    :categories/title                     "Kategorije"
    :categories/subtitle                  "Katalog kategorija za napredne korisnike (koristi se za potkategorije)."
    :categories/btn-dashboard             "Nadzorna ploča"
    :categories/delete-title              "Obriši kategoriju"
    :categories/delete-msg                "Želite li obrisati ovu kategoriju?"

    ;; Stores page
    :stores/title                         "Trgovine"
    :stores/subtitle                      "Katalog trgovina za napredne korisnike (normalizacija i pravila)."
    :stores/btn-dashboard                 "Nadzorna ploča"
    :stores/btn-store-aliases             "Pravila trgovina"
    :stores/delete-title                  "Obriši trgovinu"
    :stores/delete-msg                    "Želite li obrisati ovu trgovinu?"

    ;; Article aliases page
    :article-aliases/title                "Pravila artikala"
    :article-aliases/subtitle             "Katalog pravila za napredne korisnike (mapiranje putem neizmapirani)"
    :article-aliases/btn-dashboard        "Nadzorna ploča"
    :article-aliases/btn-unmapped         "Neizmapirana pravila"
    :article-aliases/delete-title         "Obriši pravilo artikla"
    :article-aliases/delete-msg           "Želite li obrisati ovo pravilo artikla?"

    ;; Subcategories page
    :subcategories/title                  "Potkategorije"
    :subcategories/subtitle               "Katalog potkategorija za napredne korisnike (koristi se za Artikle)."
    :subcategories/btn-dashboard          "Nadzorna ploča"
    :subcategories/delete-title           "Obriši potkategoriju"
    :subcategories/delete-msg             "Želite li obrisati ovu potkategoriju?"

    ;; Manufacturers page
    :manufacturers/title                  "Proizvođači"
    :manufacturers/subtitle               "Katalog proizvođača za napredne korisnike (koristi se za Artikle)."
    :manufacturers/btn-dashboard          "Nadzorna ploča"
    :manufacturers/delete-title           "Obriši proizvođača"
    :manufacturers/delete-msg             "Želite li obrisati ovog proizvođača?"

    ;; Expense categories page
    :expense-categories/title             "Kategorije troškova"
    :expense-categories/subtitle          "Katalog vrsta troškova za napredne korisnike."
    :expense-categories/btn-dashboard     "Nadzorna ploča"
    :expense-categories/delete-title      "Obriši kategoriju troška"
    :expense-categories/delete-msg        "Želite li obrisati ovu kategoriju troška?"

    ;; Expense contexts page
    :expense-contexts/title               "Konteksti troškova"
    :expense-contexts/subtitle            "Konteksti poput doma, nekretnine, projekta ili režija za izvještaje i filtere."
    :expense-contexts/btn-dashboard       "Nadzorna ploča"
    :expense-contexts/delete-title        "Obriši kontekst troška"
    :expense-contexts/delete-msg          "Želite li obrisati ovaj kontekst troška? Ako se već koristi, radije ga deaktivirajte."

    ;; Cities page
    :cities/title                         "Gradovi"
    :cities/subtitle                      "Katalog gradova (globalni; admin zaključan)."
    :cities/btn-dashboard                 "Nadzorna ploča"
    :cities/delete-title                  "Obriši grad"
    :cities/delete-msg                    "Želite li obrisati ovaj grad?"

    ;; Supplier aliases page
    :supplier-aliases/title               "Pravila dobavljača"
    :supplier-aliases/subtitle            "Katalog pravila dobavljača za napredne korisnike."
    :supplier-aliases/btn-dashboard       "Nadzorna ploča"
    :supplier-aliases/btn-suppliers       "Dobavljači"
    :supplier-aliases/delete-title        "Obriši pravilo dobavljača"
    :supplier-aliases/delete-msg          "Želite li obrisati ovo pravilo dobavljača?"

    ;; Store aliases page
    :store-aliases/title                  "Pravila trgovina"
    :store-aliases/subtitle               "Katalog pravila trgovina za napredne korisnike."
    :store-aliases/btn-dashboard          "Nadzorna ploča"
    :store-aliases/btn-stores             "Trgovine"
    :store-aliases/delete-title           "Obriši pravilo trgovine"
    :store-aliases/delete-msg             "Želite li obrisati ovo pravilo trgovine?"}

   :en
   {;; Suppliers page
    :suppliers/title                      "Suppliers"
    :suppliers/subtitle                   "Shared supplier catalog for your household"
    :suppliers/btn-dashboard              "Dashboard"
    :suppliers/read-only-notice           "Read-only access. Ask a household member to update suppliers."
    :suppliers/detail-title               "Supplier Details"
    :suppliers/not-found                  "Supplier not found."
    :suppliers/col-name                   "Name"
    :suppliers/col-normalized-key         "Normalized Key"
    :suppliers/col-address                "Address"
    :suppliers/col-created                "Created At"
    :suppliers/col-id                     "ID"
    :suppliers/recent-expenses-title      "Recent Expenses"
    :suppliers/recent-expenses-empty      "No expenses for this supplier yet."
    :suppliers/aliases-title              "Article Aliases"
    :suppliers/aliases-empty              "No article aliases for this supplier."
    :suppliers/col-purchased              "Purchased"
    :suppliers/col-total                  "Total"
    :suppliers/col-payer                  "Payer"
    :suppliers/col-status                 "Status"
    :suppliers/col-raw-label              "Raw Label"
    :suppliers/col-article                "Article"
    :suppliers/col-normalized             "Normalized"
    :suppliers/delete-title               "Delete supplier"
    :suppliers/delete-msg                 "Do you want to delete this supplier?"

    ;; Payers page
    :payers/title                         "Payers"
    :payers/subtitle                      "Shared payment methods for your household"
    :payers/btn-dashboard                 "Dashboard"
    :payers/read-only-notice              "Read-only access. Ask a household member to update payers."
    :payers/delete-title                  "Delete payer"
    :payers/delete-msg                    "Do you want to delete this payer?"
    :payers/delete-disabled-linked        "This payer is already used by existing expenses. Deactivate it from Edit instead of deleting it."
    :payers/delete-disabled-generic       "This payer cannot be deleted right now."
    :payers/form-add-title                "Add payer"
    :payers/form-edit-title               "Edit payer"
    :payers/form-created-success          "Payer added successfully."
    :payers/form-updated-success          "Payer updated successfully."
    :payers/form-placeholder              "e.g. Shared wallet"
    :payers/form-own-placeholder          "e.g. Visa 1234"
    :payers/type-system                   "System"
    :payers/type-custom                   "Custom"

    ;; Articles page
    :articles/title                       "Articles"
    :articles/subtitle                    "Power-user article catalog (used for mapping and aliases)"
    :articles/btn-dashboard               "Dashboard"
    :articles/btn-unmapped                "Unmapped Aliases"
    :articles/delete-title                "Delete article"
    :articles/delete-msg                  "Do you want to delete this article?"

    ;; Categories page
    :categories/title                     "Categories"
    :categories/subtitle                  "Power-user category catalog (used for Subcategories)."
    :categories/btn-dashboard             "Dashboard"
    :categories/delete-title              "Delete category"
    :categories/delete-msg                "Do you want to delete this category?"

    ;; Stores page
    :stores/title                         "Stores"
    :stores/subtitle                      "Power-user store catalog (used for normalization and store aliases)."
    :stores/btn-dashboard                 "Dashboard"
    :stores/btn-store-aliases             "Store Aliases"
    :stores/delete-title                  "Delete store"
    :stores/delete-msg                    "Do you want to delete this store?"

    ;; Article aliases page
    :article-aliases/title                "Article Aliases"
    :article-aliases/subtitle             "Power-user alias catalog (mapping is managed via Unmapped Aliases)"
    :article-aliases/btn-dashboard        "Dashboard"
    :article-aliases/btn-unmapped         "Unmapped Aliases"
    :article-aliases/delete-title         "Delete article alias"
    :article-aliases/delete-msg           "Do you want to delete this article alias?"

    ;; Subcategories page
    :subcategories/title                  "Subcategories"
    :subcategories/subtitle               "Power-user subcategory catalog (used for Articles)."
    :subcategories/btn-dashboard          "Dashboard"
    :subcategories/delete-title           "Delete subcategory"
    :subcategories/delete-msg             "Do you want to delete this subcategory?"

    ;; Manufacturers page
    :manufacturers/title                  "Manufacturers"
    :manufacturers/subtitle               "Power-user manufacturer catalog (used for Articles)."
    :manufacturers/btn-dashboard          "Dashboard"
    :manufacturers/delete-title           "Delete manufacturer"
    :manufacturers/delete-msg             "Do you want to delete this manufacturer?"

    ;; Expense categories page
    :expense-categories/title             "Expense Categories"
    :expense-categories/subtitle          "Power-user expense type catalog."
    :expense-categories/btn-dashboard     "Dashboard"
    :expense-categories/delete-title      "Delete expense category"
    :expense-categories/delete-msg        "Do you want to delete this expense category?"

    ;; Expense contexts page
    :expense-contexts/title               "Expense Contexts"
    :expense-contexts/subtitle            "Contexts such as home, property, project, or utilities for reporting and filters."
    :expense-contexts/btn-dashboard       "Dashboard"
    :expense-contexts/delete-title        "Delete expense context"
    :expense-contexts/delete-msg          "Do you want to delete this expense context? If it is already used, deactivate it instead."

    ;; Cities page
    :cities/title                         "Cities"
    :cities/subtitle                      "Cities catalog (global; admin-locked)."
    :cities/btn-dashboard                 "Dashboard"
    :cities/delete-title                  "Delete city"
    :cities/delete-msg                    "Do you want to delete this city?"

    ;; Supplier aliases page
    :supplier-aliases/title               "Supplier Aliases"
    :supplier-aliases/subtitle            "Power-user supplier alias catalog."
    :supplier-aliases/btn-dashboard       "Dashboard"
    :supplier-aliases/btn-suppliers       "Suppliers"
    :supplier-aliases/delete-title        "Delete supplier alias"
    :supplier-aliases/delete-msg          "Do you want to delete this supplier alias?"

    ;; Store aliases page
    :store-aliases/title                  "Store Aliases"
    :store-aliases/subtitle               "Power-user store alias catalog."
    :store-aliases/btn-dashboard          "Dashboard"
    :store-aliases/btn-stores             "Stores"
    :store-aliases/delete-title           "Delete store alias"
    :store-aliases/delete-msg             "Do you want to delete this store alias?"}})

(ns app.domain.frontend.expenses.components.manual-expense-form.smart-input.constants
  "Plain data constants for the smart expense input.")

(def chip-styles
  {:supplier "bg-blue-100 text-blue-800 border-blue-200"
   :store    "bg-emerald-100 text-emerald-800 border-emerald-200"
   :category "bg-purple-100 text-purple-800 border-purple-200"
  :expense-context "bg-violet-100 text-violet-800 border-violet-200"
   :article  "bg-amber-100 text-amber-800 border-amber-200"
   :payer    "bg-teal-100 text-teal-800 border-teal-200"
   :date     "bg-sky-100 text-sky-800 border-sky-200"
   :currency "bg-orange-100 text-orange-800 border-orange-200"})

;; Per-supplier color palette: each entry pairs a stronger shade for the
;; supplier chip with a lighter shade in the same hue for its store chips,
;; so the user can visually link a store to its supplier at a glance.
;;
;; Two axes of distinction:
;;   1. HUE — links a supplier to its own stores (same color family).
;;      Early slots intentionally jump across warm / cool / neutral families
;;      so the first visible suppliers do not collapse into lookalike greens.
;;   2. BORDER TREATMENT — every slot uses the same calm solid 3px border,
;;      which reads more cleanly than mixed dashed/dotted/double patterns
;;      when the user is scanning many chips at once.
;;   3. BORDER ACCENT — each supplier/store pair shares a contrasting border
;;      hue (blue → indigo, rose → fuchsia, etc.), with suppliers using the
;;      darker shade and their stores using a slightly lighter companion.
;;
;; `border-[3px]` keeps the outline visible without changing chip sizing,
;; while still letting the border color do the differentiation work.
;;
;; Tailwind only picks up class names that appear as literal strings in
;; source, so every variant is spelled out below — do not interpolate.
(def supplier-color-palette
  [{:supplier "bg-orange-200 text-orange-950 border-[3px] border-solid border-stone-700"
    :store    "bg-orange-50 text-orange-900 border-[3px] border-solid border-stone-500"}
   {:supplier "bg-blue-200 text-blue-900 border-[3px] border-solid border-indigo-700"
    :store    "bg-blue-50 text-blue-800 border-[3px] border-solid border-indigo-500"}
   {:supplier "bg-rose-200 text-rose-900 border-[3px] border-solid border-red-700"
    :store    "bg-rose-50 text-rose-800 border-[3px] border-solid border-red-500"}
   {:supplier "bg-amber-200 text-amber-900 border-[3px] border-solid border-orange-700"
    :store    "bg-amber-50 text-amber-800 border-[3px] border-solid border-orange-500"}
   {:supplier "bg-slate-200 text-slate-900 border-[3px] border-solid border-cyan-700"
    :store    "bg-slate-50 text-slate-800 border-[3px] border-solid border-cyan-500"}
   {:supplier "bg-cyan-200 text-cyan-900 border-[3px] border-solid border-sky-700"
    :store    "bg-cyan-50 text-cyan-800 border-[3px] border-solid border-sky-500"}
   {:supplier "bg-lime-200 text-lime-900 border-[3px] border-solid border-green-700"
    :store    "bg-lime-50 text-lime-800 border-[3px] border-solid border-green-500"}
   {:supplier "bg-violet-200 text-violet-900 border-[3px] border-solid border-purple-700"
    :store    "bg-violet-50 text-violet-800 border-[3px] border-solid border-purple-500"}])

(def type-button-styles
  {:supplier "bg-blue-50 hover:bg-blue-100 text-blue-700 border-blue-200"
   :store    "bg-emerald-50 hover:bg-emerald-100 text-emerald-700 border-emerald-200"
   :category "bg-purple-50 hover:bg-purple-100 text-purple-700 border-purple-200"
  :expense-context "bg-violet-50 hover:bg-violet-100 text-violet-700 border-violet-200"
   :article  "bg-amber-50 hover:bg-amber-100 text-amber-700 border-amber-200"})

(def create-events
  {:supplier :user-expenses/create-supplier-modal
   :store    :user-expenses/create-store-modal
   :category :user-expenses/create-expense-category-modal
  :expense-context :user-expenses/create-expense-context-modal
   :article  :user-expenses/create-article-modal})

(def create-field-names
  {:supplier :display_name
   :store    :display_name
   :category :name
  :expense-context :name
  :article  :canonical_name})

(ns district-registry.ui.home.page
  (:require
   [bignumber.core :as bn]
    [district.ui.web3-tx-id.subs :as tx-id-subs]
    [district-registry.ui.trading.subs :as trading-subs]
    [district.ui.component.tx-button :refer [tx-button]]
    [district.web3-utils :as web3-utils]
   [reagent.ratom :as ratom]
    [district-registry.ui.spec :as spec]
    [oops.core :refer [oget]]
    [district.ui.component.form.input :as inputs]
    [district.ui.smart-contracts.subs :as contracts-subs]
    [district-registry.ui.trading.events :as trading-events]
    [district-registry.ui.home.table :as dt]
    [cljs-web3.core :as web3]
    [cljs-web3-next.eth :as web3-eth]
    [district.ui.web3.subs :as web3-subs]
    [district-registry.ui.components.app-layout :refer [app-layout]]
    [district-registry.ui.components.nav :as nav]
    [district-registry.ui.components.stake :as stake]
    [district-registry.ui.contract.district :as district]
    [district-registry.ui.contract.hegex-nft :as hegex-nft]
    [district.format :as format]
    [district.graphql-utils :as gql-utils]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.subs :as gql]
    [district-registry.ui.subs :as subs]
    [district.ui.ipfs.subs :as ipfs-subs]
    [district.ui.now.subs :as now-subs]
    [district.ui.router.subs :as router-subs]
    [district.ui.web3-accounts.subs :as account-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]))


(defn district-image []
  (let [ipfs (subscribe [::ipfs-subs/ipfs])]
    (fn [image-hash]
      (when image-hash
        (when-let [url (:gateway @ipfs)]
          [:img.district-image {:src (str (format/ensure-trailing-slash url) image-hash)}])))))


(defn district-tile [{:keys [:district/background-image-hash
                             :district/balance-of
                             :district/description
                             :district/dnt-staked
                             :district/dnt-staked-for
                             :district/github-url
                             :district/meta-hash
                             :district/logo-image-hash
                             :district/name
                             :district/total-supply
                             :district/url
                             :reg-entry/status
                             :reg-entry/address
                             :reg-entry/challenges
                             :reg-entry/deposit
                             :reg-entry/version]
                      :as district}
                     {:keys [:status]}]
  (let [nav-to-details-props {:style {:cursor "pointer"}
                              :on-click #(dispatch [:district.ui.router.events/navigate
                                                    :route/detail
                                                    {:address address}])}]
    [:div.grid-box
     [:div.box-image nav-to-details-props
      [district-image background-image-hash]]
     [:div.box-text
      [:div.box-logo.sized nav-to-details-props
       [district-image logo-image-hash]]
      [:div.inner
       [:h2 nav-to-details-props (format/truncate name 64)]
       [:p nav-to-details-props (format/truncate description 200)]
       (when (and (= status "challenged"))
         (let [{:keys [:challenge/commit-period-end :challenge/reveal-period-end] :as challenge} (last challenges)]
           [:p.time-remaining [:b
                (when challenge
                  (let [district-status (gql-utils/gql-name->kw (:reg-entry/status district))
                        commit-period? (= district-status :reg-entry.status/commit-period)
                        remaining-time @(subscribe [::now-subs/time-remaining (gql-utils/gql-date->date (if commit-period?
                                                                                                          commit-period-end
                                                                                                          reveal-period-end))])
                        has-remaining-time? (not (format/zero-time-units? remaining-time))]

                    (cond
                      (and commit-period? has-remaining-time?)
                      (str "Vote period ends in " (format/format-time-units remaining-time {:short? true}))

                      (and commit-period? (not has-remaining-time?))
                      "Vote period ended."

                      (and (not commit-period?) has-remaining-time?)
                      (str "Reveal period ends in " (format/format-time-units remaining-time {:short? true}))

                      (and (not commit-period?) (not has-remaining-time?))
                      "Reveal period ended."

                      :else "")))]]))
       [:div.h-line]
       [stake/stake-info address]
       [stake/stake-form address {:disable-estimated-return? (= status "blacklisted")}]]
      [:div.arrow-blob
       (nav/a {:route [:route/detail {:address address}]}
              [:span.arr.icon-arrow-right])]]]))


(defn loader []
  (let [mounted? (r/atom false)]
    (fn []
      (when-not @mounted?
        (js/setTimeout #(swap! mounted? not)))
      [:div#loader-wrapper {:class (str "fade-in" (when @mounted? " visible"))}
       [:div#loader
        [:div.loader-graphic
         ;; [:img.blob.spacer {:src "/images/svg/loader-blob.svg"}]
         [:div.loader-floater
          [:img.bg.spacer {:src "/images/svg/loader-bg.svg"}]
          [:div.turbine
           [:img.base {:src "/images/svg/turbine-base.svg"}]
           [:div.wheel [:img {:src "/images/svg/turbine-blade.svg"}]]
           [:img.cover {:src "/images/svg/turbine-cover.svg"}]]
          [:div.fan
           {:data-num "1"}
           [:img.base {:src "/images/svg/fan-base.svg"}]
           [:div.wheel [:img {:src "/images/svg/fan-spokes.svg"}]]]
          [:div.fan
           {:data-num "2"}
           [:img.base {:src "/images/svg/fan-base.svg"}]
           [:div.wheel [:img {:src "/images/svg/fan-spokes.svg"}]]]]]]])))


(def ^:private table-state (r/atom {:draggable true}))


(def ^:private columns [{:path   [:option-type]
                         :header "Option Type"
                         :attrs  (fn [data] {:style {:text-align     "left"
                                                    :text-transform "uppercase"}})
                         :key    :option-type}
                        {:path   [:asset]
                         :header "Asset"
                         :attrs  (fn [data] {:style {:text-align     "left"
                                                    :text-transform "uppercase"}})
                         :key    :asset}
                        {:path   [:amount]
                         :header "Size"
                         :attrs  (fn [data] {:style {:text-align "left"}})
                         :key    :amount}
                        {:path   [:strike]
                         :header "Strike Price"
                         :attrs  (fn [data] {:style {:text-align "left"}})
                         :key    :strike}
                        {:path   [:expiration]
                         :header "Expires On"
                         :attrs  (fn [data] {:style {:text-align "left"}})
                         :key    :expiration}
                        {:path   [:premium]
                         :header "Total Cost"
                         :attrs  (fn [data] {:style {:text-align "left"}})
                         :key    :premium}])


(defn- row-key-fn
  "Return the reagent row key for the given row"
  [row row-num]
  (get-in row [:Animal :Name]))

(defn- cell-data
  "Resolve the data within a row for a specific column"
  [row cell]
  (let [{:keys [path expr]} cell]
    (or (and path
             (get-in row path))
        (and expr
             (expr row)))))

(defn- wrap-hegic [id]
  [:div.wrap-it {:on-click #(dispatch [::hegex-nft/wrap! id])}
   "Wrap"])

(defn- sell-hegex [id]
  [:span.sell-it {:on-click #(dispatch [::trading-events/create-offer id])}
   "Create an offer"])

(defn- nft-badge
  "WIP, should be a fun metadata pic"
  [id]
  [:div.wrap-it
   (str "NFT#" id)])

(defn- cell-fn
"Return the cell hiccup form for rendering.
 - render-info the specific column from :column-model
 - row the current row
 - row-num the row number
 - col-num the column number in model coordinates"
[render-info row row-num col-num]
(let [{:keys [format attrs]
       :or   {format identity
              attrs (fn [_] {})}} render-info
      data    (cell-data row render-info)
      content (format data)
      attrs   (attrs data)]
  [:span
   (assoc-in attrs [:style :position] "relative")
   content
   (when (= 0 col-num)
     (if (:hegex-id row)
       [nft-badge (:hegex-id row)]
       [wrap-hegic (:hegic-id row)]))]))


(defn date?
  "Returns true if the argument is a date, false otherwise."
  [d]
  (instance? js/Date d))

(defn date-as-sortable
  "Returns something that can be used to order dates."
  [d]
  (.getTime d))

(defn compare-vals
  "A comparator that works for the various types found in table structures.
  This is a limited implementation that expects the arguments to be of
  the same type. The :else case is to call compare, which will throw
  if the arguments are not comparable to each other or give undefined
  results otherwise.
  Both arguments can be a vector, in which case they must be of equal
  length and each element is compared in turn."
  [x y]
  (cond
    (and (vector? x)
         (vector? y)
         (= (count x) (count y)))
    (reduce #(let [r (compare (first %2) (second %2))]
               (if (not= r 0)
                 (reduced r)
                 r))
            0
            (map vector x y))

    (or (and (number? x) (number? y))
        (and (string? x) (string? y))
        (and (boolean? x) (boolean? y)))
    (compare x y)

    (and (date? x) (date? y))
    (compare (date-as-sortable x) (date-as-sortable y))

    :else ;; hope for the best... are there any other possiblities?
    (compare x y)))

(defn- sort-fn
  "Generic sort function for tabular data. Sort rows using data resolved from
  the specified columns in the column model."
  [rows column-model sorting]
  (sort (fn [row-x row-y]
          (reduce
            (fn [_ sort]
              (let [column (column-model (first sort))
                    direction (second sort)
                    cell-x (cell-data row-x column)
                    cell-y (cell-data row-y column)
                    compared (if (= direction :asc)
                               (compare-vals cell-x cell-y)
                               (compare-vals cell-y cell-x))]
                (when-not (zero? compared)
                  (reduced compared))
                ))
            0
            sorting))
        rows))

(defn- unlock-hegex [uid]
  [:div
   [:span.unlock-it {:on-click #(dispatch [::hegex-nft/delegate! uid])}
    "Unlock"]
   [:div.danger-space
    [:p.danger-caption "Transfer Hegic option to Hegex custody to unlock Hegex NFT."]
    [:p.danger-caption "The action is reversible."]]])

(defn- approve-exchange-hegex []
  [:div
   [:span.unlock-it {:on-click #(dispatch [::hegex-nft/approve-for-exchange!])}
    "Approve"]
   [:div.danger-space
    [:p.danger-caption "Approve Hegex exchange permission to trade your Hegex NFTs"]
    [:p.danger-caption "The action is reversible."]]])

(defn my-hegex-option [{:keys [id]}]
  (let [chef-address  @(subscribe [::contracts-subs/contract-address :optionchef])
        approved? @(subscribe [::trading-subs/approved-for-exchange?])
        hegic @(subscribe [::subs/hegic-by-hegex id])
        unlocked? (= chef-address (:holder hegic))
        uid (:hegic-id hegic)]
    [:div#registry-grid [:div.grid-box
      [:div.box-image
       [:img.nft-image {:src "/images/toro.jpg"}]]
      [:div.box-text
       "Hegex NFT #" id
       [:div.inner
        [:h2 "Hegex Option"]
        [:p "Tokenized Hegic Option"]
        [:p [:b "ETH"]]
        [:br]
        [:p "Expires on: " (:expiration hegic)]
        [:p "Hegic ID: " (:hegic-id hegic)]
        [:br]
        [:p "Strike price: " (:strike hegic)]
        [:br]
        (cond
          (not approved?)
          [approve-exchange-hegex]

          (not unlocked?)
          [unlock-hegex uid]

          :else
          [sell-hegex id])
        [:br]]]]]))


(defn- my-hegex-options []
  (let [ids (subscribe [::subs/my-hegex-ids])]
    [:div.grid-spaced {:style {:text-align "center"
                              :margin-top "30px"}}
     (doall (map (fn [id]
                   ^{:key id}
                   [my-hegex-option {:id id}])
                 @ids))]))


(def ^:private table-props
  {:table-container {:style {:border-radius "5px"
                             :padding       "15px"
                             :border        "1px solid #47608e"}}
   :th              {:style {:color            "#aaa"
                             :font-size        "12px"
                             :text-align       "left"
                             :background-color "#070a0e"
                             :padding-bottom   "20px"}}
   :td              {:style {:padding-bottom "10px"}}
   :table-state     table-state
   :scroll-height   "400px"
   :column-model    columns
   :row-key         row-key-fn
   :render-cell     cell-fn
   :sort            sort-fn})

(defn- my-hegic-options []
  (let [opts (subscribe [::subs/hegic-full-options])]
    [:div.container {:style {:font-size       16
                             :margin-top      10
                             ;; :text-align      "center"
                             ;; :display         "flex"
                             ;; :justify-content "center"
                             }}
    [dt/reagent-table opts table-props]]))


(defn- navigation-item [{:keys [:status :selected-status :route-query]} text]
  (let [#_query #_(subscribe [::gql/query {:queries [(build-total-count-query status)]}])]
    (fn []
      [:li {:class (when (= selected-status (name status)) "on")}
       (nav/a {:route [:route/home {} (assoc route-query :status "hegic")]
               :class (when-not (= status :blacklisted)
                        "cta-btn")}
              (str text))])))


(defn- new-hegex []
  (let [form-data (r/atom {:new-hegex/option-type :put})
        errors (ratom/reaction {:local
                                nil
                                #_(when-not (spec/check ::spec/challenge-comment
                                                      (:challenge/comment @form-data))
                                         {:challenge/comment "Comment shouldn't be empty."})})]
    (fn [{:keys [:reg-entry/address :reg-entry/status :reg-entry/deposit :district/name]}]
      [:div
      [:div.h-line]
       [:form.challenge {:style {:text-align "center"}}
       [:p "Asset: " [:b "ETH"]]
        [:br]
       [:div
        [inputs/text-input {:form-data form-data
                              :placeholder "Period, days"
                              :id :new-hegex/period
                              :errors errors}]
        [inputs/text-input {:form-data form-data
                              :placeholder "Option Size"
                              :id :new-hegex/amount
                              :errors errors}]
        [inputs/text-input {:form-data form-data
                              :placeholder "Strike Price, $"
                              :id :new-hegex/strike-price
                              :errors errors}]
        [inputs/select-input {:form-data form-data
                              :options [{:key :put  :value "Put"}
                                        {:key :call  :value "Call"}]
                              :id :new-hegex/option-type
                              :errors errors}]]
       [:div.form-btns
        [:p (format/format-dnt (web3-utils/wei->eth-number deposit))]
        [:div {:on-click #(dispatch [::hegex-nft/mint-hegex @form-data])
               :class "cta-btn"}
         "Mint"]
        #_[tx-button
         {:class "cta-btn"
          :primary true
          :disabled (-> @errors :local boolean)
          :pending?  @(subscribe [::tx-id-subs/tx-pending? {:approve-and-create-challenge {:reg-entry/address address}}])
          :pending-text "Challenging..."
          :on-click #(dispatch [::hegex-nft/mint-hegex @form-data])}
         "Mint"]]]
      [:div.h-line]])))

(defn- orderbook []
  [:div {:style {:display "flex"
                 :justify-content "center"}}
   [:div {:on-click #(dispatch [::trading-events/load-orderbook])
          :class "cta-btn"}
    "Print 0x Hegex NFT orderbook to console"]])

(defmethod page :route/home []
  (let [active-account (subscribe [::account-subs/active-account])
        route-query (subscribe [::router-subs/active-page-query])
        status (or (:status @route-query) "hegic")
        order-by (or (:order-by @route-query) "created-on")
        order-by-kw (keyword "districts.order-by" order-by)
        order-by-kw->str {:districts.order-by/created-on "Creation Date"
                          :districts.order-by/dnt-staked "DNT Staked"}
        select-menu-open? (r/atom false)
        web3? (subscribe [::web3-subs/web3-injected?])
        web3-host  (subscribe [::web3-subs/web3])
        hegex-nft-owner (subscribe [::subs/hegex-nft-owner])
        my-hegic-option (subscribe [::subs/hegic-options])]
    [:f> app-layout
     [:section#intro
      [:div.container
       [:nav.subnav
        [:ul
         [navigation-item
          {:status :hegic
           :selected-status status
           :route-query @route-query}
          "Hegic"]
         [navigation-item
          {:status :wip
           :selected-status status
           :route-query @route-query}
          "Synthetix"]]]
       [:div {:style {:text-align "center"}}
        [:h2.white  "My Option Contracts"]]]]
     #_[:div "ID of hegic option(s) I own: " (or @my-hegic-option "loading...")]

     #_(when @web3?
         [:div {:on-click #(hegex-nft/my-hegic-options @web3-host @active-account)}
          "[DEV] load hegic options owned by me (ropsten!)"])
     [:br]
     #_(when @web3?
         [:div {:on-click #(dispatch [::hegex-nft/hegic-option 0])}
          "[DEV] load Hegic option info for option #0"])
     [:div.container
      [:div.select-menu {:class (when @select-menu-open? "on")}
       #_[:div.select-choice.cta-btn
          [:div.select-text (order-by-kw order-by-kw->str)]
          [:div.arrow [:span.arr.icon-arrow-down]]]
       [:div.select-drop
        [:ul
         (->> order-by-kw->str
              keys
              (remove #(= order-by-kw %))
              (map (fn [k]
                     [:li {:key k}
                      (nav/a {:route [:route/home {} (assoc @route-query :order-by (name k))]}
                             (order-by-kw->str k))]))
              doall)]]]
      [my-hegic-options]]
     [:div {:style {:margin-top "50px"
                    :text-align "center"}}
      [:h3 "+ Mint a new Hegex NFT via Hegic"]]
     [new-hegex]

     [:div {:style {:margin-top "50px"
                    :text-align "center"}}
      [:h2.white  "My Hegex NFTs"]]
     [my-hegex-options]
     [:div {:style {:margin-top "50px"
                    :text-align "center"}}
      [:h2.white  "Hegex Option Offers [0x WIP]"]]

     [:br]
     [:br]
     [orderbook]
     [:br]
     [:br]
     [:br]]))

;; really good stuff here going on
;;and we
;;
;;
;;and do something really massive here
;;
;;
;;and
;;
;;
;; atomic swaps monero

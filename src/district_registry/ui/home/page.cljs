(ns district-registry.ui.home.page
 (:import [goog.async Debouncer])
  (:require
   [bignumber.core :as bn]
    [district.ui.web3-account-balances.subs :as account-balances-subs]
   [district-registry.ui.weth.subs :as weth-subs]
   [district-registry.ui.weth.events :as weth-events]
    [district.ui.web3-tx-id.subs :as tx-id-subs]
   [cljs-bean.core :refer [bean ->clj ->js]]
   [district-registry.ui.components.components :as c]
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

(defn debounce [f interval]
  (let [dbnc (Debouncer. f interval)]
    ;; We use apply here to support functions of various arities
    (fn [& args] (.apply (.-fire dbnc) dbnc (to-array args)))))


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


(def ^:private table-state (r/atom {:draggable false}))


(def ^:private columns [{:path   [:hegex-id]
                         :header "Hegex"
                         :attrs  (fn [data] {:style {:text-align     "left"
                                                    :text-transform "uppercase"}})
                         :key    :option-type}
                        {:path   [:option-type]
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
  [:> (c/c :button)
   {:outlined true
    :small true
    :intent :primary
    :on-click #(dispatch [::hegex-nft/wrap! id])}
   "Wrap"])

(defn- sell-hegex [open? id]
  (println "open? in sell-hegex is" open?)
  [:> (c/c :button)
   {:outlined true
    :small true
    :intent :primary
    :on-click #(reset! open? true)}
   "Sell"])


(defn- buy-hegex-offer [order]
  [:> (c/c :button)
   {:outlined true
    :small true
    :style {:margin-top "17px"
            :margin-bottom "0px"}
    :intent :primary
    :on-click #(dispatch [::trading-events/fill-offer order])}
   "Buy"])

(defn- approve-weth-exchange []
  [:> (c/c :button)
   {:outlined true
    :small true
    :style {:margin-top "17px"
            :margin-bottom "0px"}
    :intent :primary
    :on-click #(dispatch [::weth-events/approve-exchange])}
   "Approve WETH"])

(defn- approve-weth-staking []
  [:> (c/c :button)
   {:outlined true
    :small true
    :style {:margin-top "17px"
            :margin-bottom "0px"}
    :intent :primary
    :on-click #(dispatch [::weth-events/approve-staking])}
   "Approve WETH Staking"])

(defn- nft-badge
  "WIP, should be a fun metadata pic"
  [id]
  [:> (c/c :tag)
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
  (println "row is" row)
  [:div
   (cond-> (merge-with merge attrs  {:style {:padding "10px"
                                             :display "flex"
                                             :align-items "center"
                                             :min-height "47px"
                                             :position "relative"}})
     (even? row-num) (assoc-in [:style :background-color] "#212c35"))
   (if (= 0 col-num)
     (if (:hegex-id row)
       [nft-badge (:hegex-id row)]
       [wrap-hegic (:hegic-id row)])
     content)]))


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
   [:> (c/c :button)
   {:outlined true
    :small true
    :intent :primary
    :on-click #(dispatch [::hegex-nft/delegate! uid])}
    "Unlock"]
   [:div.danger-space
    [:span.danger-caption "Delegate Hegic to enable trading"]]])

(defn- approve-exchange-hegex []
  [:div
   [:> (c/c :button)
   {:outlined true
    :small true
    :intent :primary
    :on-click #(dispatch [::hegex-nft/approve-for-exchange!])}
    "Approve"]
   [:div.danger-space
    [:span.danger-caption "Approve Hegex to enable trading"]]])

(defn my-hegex-option [{:keys [id open? selling?]}]
  (let [chef-address  @(subscribe [::contracts-subs/contract-address :optionchef])
        approved? @(subscribe [::trading-subs/approved-for-exchange?])
        hegic @(subscribe [::subs/hegic-by-hegex id])
        unlocked? (= chef-address (:holder hegic))
        uid (:hegic-id hegic)]
    [:> (c/c :card)
     {:elevation 4
      :interactive true
      :class-name "hegex-option"}
     [:div
      [:> (c/c :tag)
       {:style {:margin-bottom "5px"}
        :minimal true}
       "Hegex NFT#" id]

      ;; TODO query/visalize ITM/ATM/OTM status
      [:b.special.nft-caption " ITM"]
      [:br]
      [:div {:style {:text-align "left"}}
       [:div.price-caption.primary "Tokenized Hegic Option"]
       [:span.nft-caption
        "Hegic ID: " (:hegic-id hegic)]
       [:br]
       [:span.nft-caption "Expiry: "
        (:expiration hegic)]
       [:br]
       [:span.nft-caption "Strike price: "
        (:strike hegic)]]
      [:br]
      (cond (not approved?)
            [approve-exchange-hegex]

            (not unlocked?)
            [unlock-hegex uid]

            (not selling?)
            [sell-hegex open? id])]]))

(defn- maker-input []
  (let [form-data (r/atom {:expires 0
                           :total 0})]
    (fn [{:keys [id]}]
      (println "form -data is" @form-data)
      [:div.fchild
      [:div
       [:h4 "Sellling NFT#" id]
       [:br]
       [:h2 "For "
        [:> (c/c :editable-text)
         {:intent "primary"
          :on-change (fn [e]
                     ((debounce #(swap! form-data assoc
                                        :total
                                        e)
                                500)))
          :placeholder "0"}]]
       [:h4 "WETH"]
       [:br]
       [:h2 "Expires in "
        [:> (c/c :editable-text)
         {:intent "primary"
          :on-change (fn [e]
                     ((debounce #(swap! form-data assoc
                                        :expires
                                        e)
                                500)))
          :placeholder "0"}]]
       [:h4 "hours"]
       [:br]
       [:> (c/c :button)
        {:outlined true
         :small true
         :on-click #(dispatch [::trading-events/create-offer (assoc @form-data :id id)])
         :intent :primary}
        "Place Offer"]]])))

(defn- my-hegex-option-wrapper []
  (let [open? (r/atom false)]
    (fn [{:keys [id]}]
      (println "open? " @open?)
      [:<>
       [my-hegex-option {:id id
                         :open? open?}]
       [:> (c/c :dialog)
        {:on-close #(reset! open? false)
         :portal-class-name "bp3-dark"
         :is-open @open?}
        [:div.fwrap
         [:div.fchild [my-hegex-option {:id id
                                        :selling? true
                                        :open? open?}]]
         [maker-input {:id id}]]]])))

(defn orderbook-hegex-option [offer]
  (let [chef-address  @(subscribe [::contracts-subs/contract-address :optionchef])
        weth-approved? @(subscribe [::weth-subs/exchange-approved?])
        staking-approved? @(subscribe [::weth-subs/staking-approved?])
        hegic offer
        unlocked? (= chef-address (:holder hegic))
        uid (:hegic-id hegic)]
    (println "offer is" offer)
    (println "off keys are" (keys offer))
    [:> (c/c :card)
         {:elevation 4
          :interactive true
          :class-name "hegex-option"}
     [:div
      [:> (c/c :tag)
       {:style {:margin-bottom "5px"}
        :minimal true}
        "Hegex NFT#" (:hegex-id offer)]
      ;; TODO query/visalize ITM/ATM/OTM status
      [:b.special.nft-caption " ITM"]
      [:br]
      [:div {:style {:text-align "left"}}
       [:div.price-caption.primary "Tokenized Hegic Option"]
       [:span.nft-caption
        "Hegic ID: " (:hegic-id hegic)]
       [:br]
       [:span.nft-caption "Expiry: "
        (:expiration hegic)]
       [:br]
       [:span.nft-caption "Strike price: "
        (:strike hegic)]]
      (cond
        (not weth-approved?) [approve-weth-exchange]
        (not staking-approved?) [approve-weth-staking]
        :else [buy-hegex-offer offer])
      [:br]
       [:span.price-caption.primary (:eth-price hegic) " WETH"]
      ]]))

(defn- my-hegex-options []
  (let [ids (subscribe [::subs/my-hegex-ids])]
    [:> (c/c :card)
     {:elevation 5
      :class-name "my-nfts-bg"}
     [:br]
     [:div {:style {:display "flex"
                    :flex-direction "horizontal"
                    :align-items "center"
                    :justify-content "center"}}
      [c/i {:i "person"
            :size "13"
            :class "special"}]
      [:h3.dim-icon.special {:style {:display "flex"
                             :align-items "center"
                             :margin-left "10px"}} "My"
       [:h3.special {:style {:margin-left "5px"}} "Hegex" ]
       [:span {:style {:margin-left "5px"}}"NFTs"]]]

     [:br]
     [:div.container {:style {:font-size 16
                              :text-align "center"
                              :justify-content "center"
                              :align-items "center"}}
      [:div#hegex-wrapper
       [:div#hegex-container (doall (map (fn [id]
                      ^{:key id}
                      [my-hegex-option-wrapper {:id id}])
                    @ids))]
       [:div {:style {:clear "both"}}]]]]))


(def ^:private table-props
  {:table-container {:style {:border-radius "5px"
                             :text-align "center"
                             :padding       "15px"
                            #_ :border        #_"1px solid #47608e"}}
   :th              {:style {:color            "#aaa"
                             :font-size        "12px"
                             :text-align       "left"
                             :padding   "10px"}}
   :table-state     table-state
   :table {:style {:margin "auto"}}
   ;; :scroll-height   "400px"
   :column-model    columns
   :row-key         row-key-fn
   :render-cell     cell-fn
   :sort            sort-fn})

(defn- my-hegic-options []
  (let [opts (subscribe [::subs/hegic-full-options])]
    [:> (c/c :card)
     {:elevation 5
      :class-name "hegic-table"}
     [:div {:style {:display "flex"
                    :align-items "center"
                    :justify-content "center"}}
      [c/i {:i "person"
            :size "13"
            :class "white"}]
      [:h3.dim-icon {:style {:margin-left "10px"}} "My Hegic Options"]]
     [:div.container {:style {:font-size 16
                              :text-align "center"
                              :justify-content "center"
                              :align-items "center"
                              :overflow-x "scroll"}}
      [:div {:style {:margin-left "auto" :margin-right "auto"}}
       [dt/reagent-table opts table-props]]]]))


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
    (fn []
      [:div {:style {:text-align "center"}}
       [:> (c/c :card)
        {:style {:background-color "black"
                 :margin-bottom "20px"}}
        [:div {:style {:margin "20px"
                       :margin-bottom "30px"
                       :text-align "center"}}
         [:h4.primary
          [c/i {:i "add"}]
          " Mint a fresh Hegex NFT via Hegic"]]
        [:br]
        [:> (c/c :card)
         {:elevation 4
          :interactive true
          :class-name "mint-new-card"}
         [:div {:className "bp3-body"}
          [:div
           [:h3 "Asset: " [:b.accent "ETH"]]
           [:br]
           [:> (c/c :control-group)
            {:vertical true
             :style {:max-width "230px"}}

            [:> (c/c :numeric-input)
             {:fill true
              :left-icon "calendar"
              :on-value-change (fn [e]
                                 ((debounce #(swap! form-data assoc
                                                    :new-hegex/period
                                                    e)
                                            500)))
              :placeholder "Period, days"}]

            [:> (c/c :input-group)
             {:fill true
              :left-icon "dashboard"
              :on-change  (fn [e]
                            (js/e.persist)
                            ((debounce #(swap! form-data assoc
                                               :new-hegex/amount
                                               (oget e ".?target.?value"))
                                       500)))
              :placeholder "Option Size"}]
            [:> (c/c :input-group)
             {:fill true
              :left-icon "dollar"
              :on-change  (fn [e]
                            (js/e.persist)
                            ((debounce #(swap! form-data assoc
                                               :new-hegex/strike-price
                                               (oget e ".?target.?value"))
                                       500)))
              :placeholder "Strike Price"}]
            [:> (c/c "HTMLSelect")
             {:on-change (fn [e]
                           (js/e.persist)
                           ((debounce #(swap! form-data
                                              assoc
                                              :new-hegex/option-type
                                              (oget e ".?target.?value"))
                                      500)))}
             [:option {:selected true
                       :value :put}
              "Put"]
             [:option {:selected true
                       :value :call}
              "Call"]]]
           [:br]
           [:br]
           [:> (c/c :button)
            {:outlined true
             :large true
             :on-click #(dispatch [::hegex-nft/mint-hegex @form-data])}
            "Mint"]]]]]])))

(defn- convert-weth []
(let [form-data (r/atom {:weth/type :wrap})]
  (fn []
    (let [form-res (case (some-> @form-data :weth/type keyword)
                    :wrap {:btn "Wrap"
                           :evt ::weth-events/wrap}
                    :unwrap {:btn "Unwrap"
                             :evt ::weth-events/unwrap}
                    {:btn "Wrap"
                     :evt ::weth-events/wrap})]
     (println "form-data is" @form-data)
     [:div {:style {:max-width "250px"
                    :margin-left "auto"
                    :margin-right "auto"
                    :text-align "center"}}
      [:br]
      [:br]
      [:div {:style {:max-width "250px"}}
       [:> (c/c :control-group)
        {:vertical false}
        [:> (c/c "HTMLSelect")
         {:on-change (fn [e]
                       (js/e.persist)
                       ((debounce #(swap! form-data
                                          assoc
                                          :weth/type
                                          (oget e ".?target.?value"))
                                  500)))}
         [:option {:value :wrap}
          "Wrap"]
         [:option {:value :unwrap}
          "Unwrap"]]
        [:> (c/c :input-group)
         {:fill true
          :left-lable "WETH"
          :on-change  (fn [e]
                        (js/e.persist)
                        ((debounce #(swap! form-data assoc
                                           :weth/amount
                                           (oget e ".?target.?value"))
                                   500)))
          :placeholder "Amount"}]
        [:> (c/c :button)
         {:outlined true
          :on-click #(dispatch [(:evt form-res) @form-data])}
         (:btn form-res)]]]]))))

(defn- orderbook []
  (let [weth-bal @(subscribe [::weth-subs/balance])
        book @(subscribe [::trading-subs/hegic-book])
        eth-bal (some-> (subscribe
                          [::account-balances-subs/active-account-balance :ETH])
                         deref
                         web3-utils/wei->eth-number
                         (format/format-number {:max-fraction-digits 5}))]
    [:> (c/c :card)
     {:elevation 5
      :class-name "trade-nfts-bg"}
     [:br]
     [:div {:style {:display "flex"
                    :flex-direction "horizontal"
                    :align-items "center"
                    :justify-content "center"}}
      [c/i {:i "exchange"
            :size "25"
            :class "primary"}]
      [:h3.dim-icon.primary {:style {:display "flex"
                             :align-items "center"
                             :margin-left "10px"}} "Trade"
       [:h3.primary {:style {:margin-left "5px"}} "Hegex" ]
       [:span {:style {:margin-left "5px"}}"NFTs"]]]

     [:br]
     [:div {:style {:text-align "center"}}
      [:> (c/c :button)
       {:outlined true
        :small true
        :on-click #(dispatch [::trading-events/load-orderbook])
        :intent :primary}
       "Force orderbook update"]]
     [:br]
     [:div {:style {:text-align "center"}}
      [:p "You need some WETH to buy Hegex NFTs"]
      [:> (c/c :tag)
       {:intent "secondary"
        :minimal true}
       eth-bal " ETH"]
      " "
      [:> (c/c :tag)
       {:intent "secondary"
        :minimal true}
       weth-bal " WETH"]]
     [convert-weth]
     [:br]
     [:br]
     [:br]
     [:div.container {:style {:font-size 16
                              :text-align "center"
                              :justify-content "center"
                              :align-items "center"}}
      [:div#hegex-wrapper
       [:div#hegex-container (doall (map (fn [offer]
                      ^{:key (:hegex-id offer)}
                      [orderbook-hegex-option offer])
                    book))]
       [:div {:style {:clear "both"}}]]]]))

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
;; <Tabs id="TabsExample" onChange={this.handleTabChange} selectedTabId="rx">
;;     <Tab id="ng" title="Angular" panel={<AngularPanel />} />
;;     <Tab id="mb" title="Ember" panel={<EmberPanel />} panelClassName="ember-panel" />
;;     <Tab id="rx" title="React" panel={<ReactPanel />} />
;;     <Tab id="bb" disabled title="Backbone" panel={<BackbonePanel />} />
;;     <Tabs.Expander />
;;     <input className="bp3-input" type="text" placeholder="Search..." />
;; </Tabs>
    [app-layout
     [:section#intro
      [:div.container
       [:h4 {:style {:opacity 0.2}}
        [c/i {:i "left-join"}] " Option Type"]
       [:> (c/c :tabs)
        {:on-change constantly
         :large true
         :selected-tab-id "hegic"}
        [:> (c/c :tab)
         {:id "hegic"
          :large true
          :title "Hegic"}]
        [:> (c/c :tab)
         {:id "synthetix"
          :large true
          :title "Synthetix [WIP]"
          :disabled true}]]
       [:br]]]
     [my-hegic-options]
     [new-hegex]
     [my-hegex-options]
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

(ns district-registry.ui.home.page
  (:require
    [bignumber.core :as bn]
    [oops.core :refer [oget]]
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

(defn build-query [active-account route-query]
  [:search-districts
   {:order-by (keyword "districts.order-by" (:order-by route-query))
    :order-dir :desc
    :statuses (case (:status route-query)
                "in-registry" [:reg-entry.status/challenge-period
                               :reg-entry.status/commit-period
                               :reg-entry.status/reveal-period
                               :reg-entry.status/whitelisted]
                "challenged" [:reg-entry.status/commit-period
                              :reg-entry.status/reveal-period]
                "blacklisted" [:reg-entry.status/blacklisted]
                [:reg-entry.status/blacklisted])
    :first 1000}
   [:total-count
    :end-cursor
    :has-next-page
    [:items [:reg-entry/address
             :reg-entry/version
             :reg-entry/status
             :reg-entry/creator
             :reg-entry/deposit
             :reg-entry/created-on
             :reg-entry/challenge-period-end
             [:reg-entry/challenges
              [:challenge/index
               :challenge/challenger
               :challenge/commit-period-end
               :challenge/reveal-period-end]]
             :district/meta-hash
             :district/name
             :district/description
             :district/url
             :district/github-url
             :district/logo-image-hash
             :district/background-image-hash
             :district/dnt-staked
             :district/total-supply
             [:district/dnt-staked-for {:staker active-account}]
             [:district/balance-of {:staker active-account}]]]]])


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


(defn district-tiles [active-account route-query]
  (let [q (subscribe [::gql/query
                      {:queries [(build-query active-account route-query)]}
                      {:refetch-on #{::district/approve-and-stake-for-success
                                     ::district/unstake-success}}])
        result (:search-districts @q)
        districts (:items result)]
    (cond
      (:graphql/loading? @q) [loader]
      (empty? districts) [:div.no-districts
                          [:h2 "No districts found"]]
      :else [:div.grid.spaced
             (->> districts
               (map (fn [{:as district
                          :keys [:reg-entry/address]}]
                      ^{:key address} [district-tile district route-query]))
               doall)])))

;; generate some dummy data
(def table-data (r/atom
                  [{:Animal {:Name    "Lizard"
                             :Colour  "Green"
                             :Skin    "Leathery"
                             :Weight  100
                             :Age     10
                             :Hostile false}}
                   {:Animal {:Name    "Lion"
                             :Colour  "Gold"
                             :Skin    "Furry"
                             :Weight  190000
                             :Age     4
                             :Hostile true}}
                   {:Animal {:Name    "Giraffe"
                             :Colour  "Green"
                             :Skin    "Hairy"
                             :Weight  1200000
                             :Age     8
                             :Hostile false}}
                   {:Animal {:Name    "Cat"
                             :Colour  "Black"
                             :Skin    "Furry"
                             :Weight  5500
                             :Age     6
                             :Hostile false}}
                   {:Animal {:Name    "Capybara"
                             :Colour  "Brown"
                             :Skin    "Hairy"
                             :Weight  45000
                             :Age     12
                             :Hostile false}}
                   {:Animal {:Name    "Bear"
                             :Colour  "Brown"
                             :Skin    "Furry"
                             :Weight  600000
                             :Age     10
                             :Hostile true}}
                   {:Animal {:Name    "Rabbit"
                             :Colour  "White"
                             :Skin    "Furry"
                             :Weight  1000
                             :Age     6
                             :Hostile false}}
                   {:Animal {:Name    "Fish"
                             :Colour  "Gold"
                             :Skin    "Scaly"
                             :Weight  50
                             :Age     5
                             :Hostile false}}
                   {:Animal {:Name    "Hippo"
                             :Colour  "Grey"
                             :Skin    "Leathery"
                             :Weight  1800000
                             :Age     10
                             :Hostile false}}
                   {:Animal {:Name    "Zebra"
                             :Colour  "Black/White"
                             :Skin    "Hairy"
                             :Weight  200000
                             :Age     9
                             :Hostile false}}
                   {:Animal {:Name    "Squirrel"
                             :Colour  "Grey"
                             :Skin    "Furry"
                             :Weight  300
                             :Age     1
                             :Hostile false}}
                   {:Animal {:Name    "Crocodile"
                             :Colour  "Green"
                             :Skin    "Leathery"
                             :Weight  500000
                             :Age     10
                             :Hostile true}}]))

(def table-state (r/atom {:draggable false}))


(def columns [{:path [:Animal :Name]
               :header "Option Type"
               :key :Name}  ; convention - use field name for reagent key
              {:path [:Animal :Colour]
               :header "Currency"
               :key :Colour}
              {:path [:Animal :Skin]
               :header "Size"
               :key :Skin}
              {:path [:Animal :Weight]
               :header "Strike Price"
               :attrs (fn [data] {:style {:text-align "right"
                                          :display "block"}})
               :key :Weight}
              {:path [:Animal :Age]
               :header "Total Cost"
               :attrs (fn [data] {:style {:text-align "right"
                                          :display "block"}})
               :key :Age}
              {:path [:Animal :Hostile]
               :header "Period"
               :attrs (fn [data] {:style {:text-align "right"
                                          :display "block"}})
               :key :Hostile}])


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
   attrs
   content]))


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

(defn- my-hegic-options []
 [:div.container {:style {:font-size 16 :margin-top 10 :text-align "center"} }
  ;[:div.panel.panel-default
   ;[:div.panel-body
    [dt/reagent-table table-data {:table {:class "table table-hover table-striped table-bordered table-transition"
                                          :style {:border-spacing 0
                                                  :border-collapse "separate"}}
                                  :table-container {:style {:border-radius "5px"
                                                            :padding "15px"
                                                            :border "1px solid #47608e"}}
     :th {:style {:color "#aaa"
                   :font-size "12px"
                  :text-align "left"
                  :background-color "#070a0e"
                  :padding-bottom "20px"}}
                                  :table-state  table-state
                                  :scroll-height "400px"
                                  :column-model columns
                                  :row-key      row-key-fn
                                  :render-cell  cell-fn
                                  :sort         sort-fn}]]
  #_[:div {:style {:display "flex"
                 :justify-content "center"}}
   [dt/reagent-table table-data
    {:table {:style {:border-spacing 0
                     :border-collapse "separate"}}
     :table-container {:style {}}
     :th {:style {:color "#aaa"
                   :font-size "12px"
                  :text-align "left"
                  :background-color "#070a0e"
                  :padding-bottom "20px"}}
     :table-state  table-state
     :scroll-height "500px"
     :column-model columns
     :row-key      row-key-fn
     :render-cell  cell-fn
     :sort         sort-fn}]])


(defn- navigation-item [{:keys [:status :selected-status :route-query]} text]
  (let [#_query #_(subscribe [::gql/query {:queries [(build-total-count-query status)]}])]
    (fn []
      [:li {:class (when (= selected-status (name status)) "on")}
       (nav/a {:route [:route/home {} (assoc route-query :status "hegic")]
               :class (when-not (= status :blacklisted)
                        "cta-btn")}
              (str text))])))


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
    (fn []
      [app-layout
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
          [:h2  "My option contracts"]]]]
         #_[:div "ID of hegic option(s) I own: " (or @my-hegic-option "click btn below to load")]
       #_(when @web3?
         [:div {:on-click #(hegex-nft/my-hegic-options @web3-host @active-account)}
         "[DEV] load hegic options owned by me (ropsten!)"])
       [:section#registry-grid
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
         [my-hegic-options]
         #_[district-tiles @active-account (assoc @route-query
                                           :status status
                                           :order-by order-by)]]]])))

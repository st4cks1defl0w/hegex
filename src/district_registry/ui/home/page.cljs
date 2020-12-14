(ns district-registry.ui.home.page
  (:require
    [bignumber.core :as bn]
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


(defn- build-total-count-query [status-group]
  (let [statuses (case status-group
                   :in-registry [:reg-entry.status/challenge-period
                                 :reg-entry.status/commit-period
                                 :reg-entry.status/reveal-period
                                 :reg-entry.status/whitelisted]
                   :challenged [:reg-entry.status/commit-period
                                :reg-entry.status/reveal-period]
                   :blacklisted [:reg-entry.status/blacklisted]
                   [:reg-entry.status/blacklisted])]
    [:search-districts
     {:order-by :districts.order-by/created-on
      :order-dir :desc
      :statuses statuses
      :first 0}
     [:total-count]]))


(defn- navigation-item [{:keys [:status :selected-status :route-query]} text]
  (let [#_query #_(subscribe [::gql/query {:queries [(build-total-count-query status)]}])]
    (fn []
      [:li {:class (when (= selected-status (name status)) "on")}
       (nav/a {:route [:route/home {} (assoc route-query :status (name status))]
               :class (when-not (= status :blacklisted)
                        "cta-btn")}
              (str text #_(when-let [total-count (-> @query :search-districts :total-count)]
                          (str " (" total-count ")"))))])))


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
        #_[:div.bg-wrap
         [:div.background.sized
          [:img {:src "/images/blobbg-top@2x.png"}]]]
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
            "Synthetix"]
           #_[navigation-item
            {:status :blacklisted
             :selected-status status
             :route-query @route-query}
            "Blacklisted"]]]
         [:h2 "My options"]
         [:br]
         [:div "Web3 on?" (str @web3?)]
         (when @web3? [:div "Web3 is" (str  (type @web3-host))])
         (when @web3? [:div "Web3 accs are" (str  (web3-eth/accounts @web3-host))])
         [:br]
         [:br]
         [:div "Current owner is " (or @hegex-nft-owner "click btn below to load")]
         [:div {:on-click hegex-nft/deb-owner}
          "[DEV] load hegex nft owner"]]]
         [:br]
         [:br]
         [:div "ID of hegic option(s) I own: " (or @my-hegic-option "click btn below to load")]
       (when @web3?
         [:div {:on-click #(hegex-nft/get-event @web3-host)}
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
         [district-tiles @active-account (assoc @route-query
                                           :status status
                                           :order-by order-by)]]]])))

(ns district-registry.ui.detail.page
  (:require
    [bignumber.core :as bn]
    [cljsjs.bignumber]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as str]
    [district-registry.ui.components.app-layout :refer [app-layout]]
    [district-registry.ui.components.stake :as stake]
    [district-registry.ui.contract.registry-entry :as reg-entry]
    [district-registry.ui.events :as events]
    [district-registry.ui.not-found.page :as not-found]
    [district-registry.ui.spec :as spec]
    [district-registry.ui.utils :as ui-utils]
    [district.format :as format]
    [district.graphql-utils :as gql-utils]
    [district.parsers :as parsers]
    [district.ui.component.form.input :as inputs]
    [district.ui.component.page :refer [page]]
    [district.ui.component.tx-button :refer [tx-button]]
    [district.ui.graphql.subs :as gql]
    [district.ui.now.subs :as now-subs]
    [district.ui.router.subs :as router-subs]
    [district.ui.web3-account-balances.subs :as account-balances-subs]
    [district.ui.web3-accounts.subs :as account-subs]
    [district.ui.web3-tx-id.subs :as tx-id-subs]
    [district.web3-utils :as web3-utils]
    [re-frame.core :refer [dispatch subscribe]]
    [reagent.core :as r]
    [reagent.ratom :as ratom]))

(defn build-query [{:keys [:district :active-account]}]
  [:district {:reg-entry/address district}
   [:reg-entry/address
    :reg-entry/version
    :reg-entry/status
    :reg-entry/creator
    :reg-entry/deposit
    :reg-entry/created-on
    :reg-entry/challenge-period-end
    [:reg-entry/challenges
     [:challenge/challenger
      :challenge/comment
      :challenge/created-on
      :challenge/reward-pool
      :challenge/commit-period-end
      :challenge/reveal-period-end
      :challenge/votes-include
      :challenge/votes-exclude
      :challenge/votes-total
      :challenge/claimed-reward-on
      [:challenge/vote {:voter active-account}
       [:vote/secret-hash
        :vote/option
        :vote/amount
        :vote/revealed-on
        :vote/claimed-reward-on
        :vote/reward]]]]
    :district/meta-hash
    :district/name
    :district/description
    :district/url
    :district/github-url
    :district/logo-image-hash
    :district/background-image-hash
    :district/dnt-weight
    :district/dnt-staked
    :district/total-supply
    [:district/dnt-staked-for {:staker active-account}]
    [:district/balance-of {:staker active-account}]]])


(defn normalize-status [status]
  (case (gql-utils/gql-name->kw status)
    (:reg-entry.status/challenge-period :reg-entry.status/whitelisted) :in-registry
    (:reg-entry.status/commit-period :reg-entry.status/reveal-period) :challenged
    :reg-entry.status/blacklisted :blacklisted))


(defn district-background [image-hash]
  (when image-hash
    (let [gateway (subscribe [::gql/query {:queries [[:config [[:ipfs [:gateway]]]]]}])]
      (when-not (:graphql/loading? @gateway)
        (if-let [url (-> @gateway :config :ipfs :gateway)]
          [:div.background-image {:style {:background-image (str "url('" (format/ensure-trailing-slash url) image-hash "')")}}
           [:img {:src "/images/district-bg-mask.png"}]])))))

(defn info-section [{:keys [:district/name
                            :district/description
                            :district/background-image-hash
                            :district/logo-image-hash
                            :district/url
                            :district/github-url
                            :district/total-supply
                            :district/dnt-staked
                            :reg-entry/status
                            :reg-entry/created-on]}]
  [:div.box-wrap.overview
   [:div.back-arrow {:on-click #(dispatch [:district.ui.router.events/navigate :route/home])}
    [:span.icon-arrow-right]]
   [:div.body-text
    [:div.container
     [:div.overview-details
      [:div.col.txt
       [:div.title-wrap.spaced
        [:div.title-txt
         [:h1 name]
         [:a {:href url} url]]
        [:div.title-icons
         [:div.title-icon
          [:a {:href github-url :target :_blank}
           [:img {:src "/images/icon-fc-github@2x.png"}]]]
         [:div.title-icon                                   ; TODO Aragon link
          [:img {:src "/images/icon-fc-bird@2x.png"}]]]]
       [:ul.details-list
        [:li (str "Status: " (-> status
                               normalize-status
                               cljs.core/name
                               str/capitalize))]
        [:li (str "Added: " (-> created-on
                              gql-utils/gql-date->date
                              format/format-local-date))]
        [:li (str "Staked total: " (-> dnt-staked
                                     web3-utils/wei->eth-number
                                     format/format-dnt))]
        [:li (str "Voting tokens issued: " (-> total-supply
                                             web3-utils/wei->eth-number
                                             format/format-number))]]
       [:nav.social
        [:ul
         [:li
          [:a {:target "_blank"
               :href (str "https://www.facebook.com/sharer/sharer.php?u=" js/window.location.href)}
           [:span.icon-facebook]]]
         [:li [:a {:target "_blank"
                   :href (str "https://twitter.com/home?status=" js/window.location.href)}
               [:span.icon-twitter]]]]]]
      ;; TODO: We aren't showing the logo image?
      [:div.col.img
       [district-background background-image-hash]]]
     [:pre.district-description description]]]])

(defn stake-section [{:keys [:reg-entry/address :reg-entry/status :district/dnt-weight]}]
  [:div
   [:h2 "Stake"]
   [:p
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec a augue quis metus sollicitudin mattis. Duis efficitur tellus felis, et tincidunt turpis aliquet non. Aenean augue metus, malesuada non rutrum ut, ornare ac orci."]
   [:h3 "Voting Token Issuance Curve"]
   [:img.spacer {:src (str "/images/curve-graph-" dnt-weight "-l.svg")}]
   [:div.stake
    [:div.row.spaced
     [stake/stake-info address]
     [stake/stake-form address]]]])

(defn challenge-section []
  (let [form-data (r/atom {:challenge/comment nil})
        errors (ratom/reaction {:local (when-not (spec/check ::spec/challenge-comment (:challenge/comment @form-data))
                                         {:challenge/comment "Comment shouldn't be empty."})})]
    (fn [{:keys [:reg-entry/address :reg-entry/status :reg-entry/deposit]}]
      (when (= (normalize-status status) :in-registry)
        (let [tx-pending? @(subscribe [::tx-id-subs/tx-pending? {:approve-and-create-challenge {:reg-entry/address address}}])]
          [:div
           [:div.h-line]
           [:h2 "Challenge"]
           [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec a augue quis metus sollicitudin mattis. Duis efficitur tellus felis, et tincidunt turpis aliquet non. Aenean augue metus, malesuada non rutrum ut, ornare ac orci."]
           [:form.challenge
            [inputs/textarea-input {:form-data form-data
                                    :id :challenge/comment
                                    :errors errors}]
            [:div.form-btns
             [:p (format/format-dnt (web3-utils/wei->eth-number deposit))]
             [tx-button
              {:class "cta-btn"
               :primary true
               :disabled (-> @errors :local boolean)
               :pending? tx-pending?
               :pending-text "Challenging..."
               :on-click (fn [e]
                           (js-invoke e "preventDefault")
                           (dispatch [::events/add-challenge
                                      {:reg-entry/address address
                                       :comment (:challenge/comment @form-data)
                                       :deposit deposit}]))}
              "Challenge"]]]])))))


(defn format-remaining-time [to-time]
  (let [time-remaining (subscribe [::now-subs/time-remaining to-time])
        {:keys [:days :hours :minutes :seconds]} @time-remaining]
    (when-not (every? zero? [days hours minutes seconds])
      (str (format/pluralize days "day") " " (format/pluralize hours "hour")
           " " minutes " min. " seconds " sec."))))


(defn- dispatch-vote [e option address form-data]
  (js-invoke e "preventDefault")
  (dispatch [::reg-entry/approve-and-commit-vote
             {:reg-entry/address address
              :vote/option option
              :vote/amount (-> form-data :vote/amount parsers/parse-float web3-utils/eth->wei)}]))


(defn- vote-button-disabled? [form-data balance-dnt]
  (let [amount (parsers/parse-float (:vote/amount form-data))]
    (or
      (not amount)
      (bn/> (web3-utils/eth->wei amount) balance-dnt))))


(defn vote-commit-section []
  (let [balance-dnt (subscribe [::account-balances-subs/active-account-balance :DNT])
        form-data (r/atom {:vote/amount ""})
        errors (ratom/reaction {:local {}})]
    (fn [{:keys [:reg-entry/address :reg-entry/status :reg-entry/challenges]}]
      (when (= :reg-entry.status/commit-period (gql-utils/gql-name->kw status))
        (let [{:keys [:challenge/commit-period-end]} (last challenges)
              tx-pending? @(subscribe [::tx-id-subs/tx-pending? {:approve-and-commit-vote {:reg-entry/address address}}])
              remaining-time (format-remaining-time (ui-utils/gql-date->date commit-period-end))]
          [:div
           [:div.h-line]
           [:h2 "Vote"]
           [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec a augue quis metus sollicitudin mattis. Duis efficitur tellus felis, et tincidunt turpis aliquet non. Aenean augue metus, malesuada non rutrum ut, ornare ac orci."]
           [:form.voting
            [:div.row.spaced
             [:pre.challenge-comment (str "\"" (-> challenges last :challenge/comment) "\"")]]
            [:div.row.spaced
             [:b "Voting period "
              (if remaining-time
                (str "ends in " remaining-time)
                "ended.")]
             [:div.form-btns
              [:div.cta-btns
               [tx-button
                {:class "cta-btn"
                 :pending? tx-pending?
                 :disabled (vote-button-disabled? @form-data @balance-dnt)
                 :pending-text "Voting..."
                 :on-click #(dispatch-vote % :vote.option/include address @form-data)}
                "Vote For"]
               [tx-button
                {:class "cta-btn"
                 :pending? tx-pending?
                 :disabled (vote-button-disabled? @form-data @balance-dnt)
                 :pending-text "Voting..."
                 :on-click #(dispatch-vote % :vote.option/exclude address @form-data)}
                "Vote Against"]]
              [:fieldset
               [inputs/amount-input
                {:class "dnt-input"
                 :form-data form-data
                 :id :vote/amount
                 :errors errors
                 :type :number
                 :disabled (nil? remaining-time)}]
               [:span.cur "DNT"]]]
             [:div
              [:p "You can vote with up to "
               (-> @balance-dnt
                 web3-utils/wei->eth-number
                 format/format-dnt)
               "."
               [:br]
               "Tokens will be returned to you after revealing your vote."]]]]])))))


(defn vote-reveal-section []
  (fn [{:keys [:reg-entry/address :reg-entry/status :reg-entry/challenges]}]
    (when (= :reg-entry.status/reveal-period (gql-utils/gql-name->kw status))
      (let [{:keys [:challenge/reveal-period-end :challenge/vote]} (last challenges)
            tx-pending? (subscribe [::tx-id-subs/tx-pending? {:reveal-vote {:reg-entry/address address}}])
            tx-success? (subscribe [::tx-id-subs/tx-success? {:reveal-vote {:reg-entry/address address}}])
            remaining-time (format-remaining-time (ui-utils/gql-date->date reveal-period-end))]
        [:div
         [:div.h-line]
         [:h2 "Reveal"]
         [:p
          "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec a augue quis metus sollicitudin mattis. Duis efficitur tellus felis, et tincidunt turpis aliquet non. Aenean augue metus, malesuada non rutrum ut, ornare ac orci."]
         [:form.voting
          [:div.row.spaced
           [:p (str "Reveal period " (if remaining-time
                                       (str "ends in " remaining-time)
                                       "ended."))]
           [tx-button
            {:class "cta-btn"
             :primary true
             :disabled (or @tx-success? (not remaining-time))
             :pending? @tx-pending?
             :pending-text "Revealing..."
             :on-click #(dispatch [::reg-entry/reveal-vote {:reg-entry/address address}])}
            "Reveal My Vote"]]]]))))


(defn main [props]
  (let [query (subscribe [::gql/query
                          {:queries [(build-query props)]}
                          {:refetch-on #{::reg-entry/approve-and-create-challenge-success}}])
        {:keys [district]} @query]
    (cond
      (nil? district) nil
      (-> district :reg-entry/address nil?) [not-found/not-found]
      :else [:section#main
             [:div.container
              [info-section district]
              [:div.box-wrap.stats
               [:div.body-text
                [:div.container
                 [stake-section district]
                 [challenge-section district]
                 [vote-commit-section district]
                 [vote-reveal-section district]]]]]])))

(defmethod page :route/detail [& x]
  (let [params (subscribe [::router-subs/active-page-params])
        active-account (subscribe [::account-subs/active-account])]
    [app-layout
     [main {:district (:address @params)
            :active-account @active-account}]]))
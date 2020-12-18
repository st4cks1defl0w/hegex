(ns district-registry.ui.contract.hegex-nft
  (:require
   [bignumber.core :as bn]
   [goog.string :as gstring]

stacked-snackbars
   [cljs-time.format :as tf]
    [goog.string.format]
   [reagent.core :as r]
   #_[web3 :as gweb3js]
   #_  ["web3" :as web3new]
   [web3 :as web3webpack]
   [district.ui.smart-contracts.subs :as contracts-subs]
   [district.ui.web3-accounts.subs :as accounts-subs]
   [re-frame.core :refer [subscribe dispatch]]
   [cljs-web3-next.eth :as web3-ethn]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer-macros [<p!]]
#_   [web3 :as web3js]
   #_[react :refer [createElement]]
  #_ ["react-dom/server" :as ReactDOMServer :refer [renderToString]]
   [cljs-bean.core :refer [bean ->clj ->js]]
   #_ [cljs-web3.core :as web3]
    [oops.core :refer [oget oset! ocall oapply ocall! oapply!
                       gget
                       oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]
    [district-registry.shared.utils :refer [debounce]]
    [cljs-web3.eth :as web3-eth]
    [cljs.spec.alpha :as s]
    [district.format :as format]
    [district.parsers :as parsers]
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [district.ui.web3.queries :as web3-queries]
    [district.web3-utils :as web3-utils]
    [goog.string :as gstring]
    [print.foo :refer [look] :include-macros true]
    [re-frame.core :as re-frame :refer [dispatch reg-event-fx]]))

(def interceptors [re-frame/trim-v])

(re-frame/reg-event-fx
  ::owner
  interceptors
  (fn [{:keys [db]} _]
    {:web3/call
     {:web3 (web3-queries/web3 db)
      :fns [{:instance (contract-queries/instance db :hegexoption)
             :fn :owner
             :on-success [::owner-success]
             :on-error [::logging/error [::owner]]}]}}))


(re-frame/reg-event-fx
  ::owner-success
  interceptors
  (fn [{:keys [db]} [owner]]
    (println "dbg" "owner is" owner)
    {:db (assoc-in db [::owner] owner)}))


(re-frame/reg-event-fx
  ::hegic-options
  interceptors
  (fn [{:keys [db]} [opt-ids]]
    (println "dispatching" (mapv (fn [id] [::hegic-option id]) opt-ids))
    {:dispatch-n (mapv (fn [id] [::hegic-option id]) opt-ids)
     :db (assoc-in db [::hegic-options :my :ids] opt-ids)}))

(def deb-owner
  (debounce
   (fn []
     (dispatch [::owner]))
    500))

(defn- ->topic-pad [w3 s]
  ;; (println "->topic-pad" (oget web3js ".?version"))
  (ocall w3 ".?utils.?padLeft" s 64))

(defn- ->from-topic-pad [w3 s]
  ;; (println "->topic-pad" (oget web3js ".?version"))
  (ocall w3 ".?utils.?hexToNumber" s))

(def ^:private creation-topic
  "0x9acccf962da4ed9c3db3a1beedb70b0d4c3f6a69c170baca7198a74548b5ef4e")

;; sample address 0xB95Fe51930dDFC546Ff766d59288b50170244B4A
(defn my-hegic-options
  "using up-to-date instance of web3 out of npm [ROPSTEN]"
  [web3-host addr]
  (let [Web3 web3webpack
        _ (println "web3 is" Web3)
        _ (println "snackbars are" (r/adapt-react-class stacked-snackbars))
        web3js (Web3. (gget ".?web3.?currentProvider"))
        #_ (ocall! js/window.ethereum "enable")]
    (println "mho"  (oget web3js ".?version"))
    ;; (println web3 #_(gget "Web3"))
    ;; TODO: migrate .getPastLogs call to oops if munged in :advanced
    ;; TODO: add transferred options (pull by transfer topic + receiver)
    (.then (.getPastLogs (oget web3js "eth") #_js/web3.eth.getPastLogs
            (clj->js {:address "0x77041D13e0B9587e0062239d083b51cB6d81404D"
                      :topics [creation-topic,
                               nil,
                               (->topic-pad web3js addr)]
                      :fromBlock 0
                      :toBlock "latest"}))
           (fn [evs]
             (let [ids-raw (map (fn [e] (-> e bean :topics second)) evs)]
               (dispatch [::hegic-options (map (partial ->from-topic-pad web3js)
                                               ids-raw)]))))))




#_(defn get-event
  "a rewrite using web3-cljs lib [ROPSTEN] - disfunctional

   other reason not to use 0.2.x is that decoding logs is tricky
   perhaps cljs-web3-next/return-values->clj etc can be leveraged when refactoring"
  [oldweb3]
  (let [logparams {:fromBlock 0
                   :toBlock "latest"
                   :address "0x77041D13e0B9587e0062239d083b51cB6d81404D"
                   :topics ["0x9acccf962da4ed9c3db3a1beedb70b0d4c3f6a69c170baca7198a74548b5ef4e", nil, "0x000000000000000000000000b95fe51930ddfc546ff766d59288b50170244b4a"]}]

    (println  (.get (web3-eth/filter oldweb3 logparams)
                    (fn [lgs] (println "dbg filters......" lgs) )))

    #_(web3-eth/contract-get-data
    (contract-queries/instance db :district)
    :stake-for
    (account-queries/active-account db)
    amount)))


;; TODO - look into batching for this web3 fx
(re-frame/reg-event-fx
  ::hegic-option
  interceptors
  (fn [{:keys [db]} [id]]
    (println "dbg fetching full data for option id " id "..." )
    {:web3/call
     {:web3 (web3-queries/web3 db)
      :fns [{:instance (contract-queries/instance db :brokenethoptions)
             :fn :options
             :args [id]
             :on-success [::hegic-option-success id]
             :on-error [::logging/error [::hegic-option]]}]}}))

(re-frame/reg-event-fx
  ::hegic-option-success
  interceptors
  (fn [{:keys [db]} [id [state holder strike amount
                     locked-amount premium expiration
                        option-type]]]
    ;; NOTE move formatting to view, store raw data in re-frame db
    {:db (assoc-in db [::hegic-options :full id]
                   {:state         (bn/number state)
                    ;;data redundancy for ease of access by views
                    :hegic-id      id
                    :holder        holder
                    :strike        (some->> strike
                                            bn/number
                                            (*  0.00000001)
                                            (gstring/format "%.2f")
                                            (str "$"))
                    :amount        (some->> amount
                                            bn/number
                                            (*  0.001)
                                            (gstring/format "%.3f")
                                            (str "kWei "))
                    :locked-amount (bn/number locked-amount)
                    :premium       (some->> premium
                                            bn/number
                                            (*  0.00000001)
                                            (gstring/format "%.3f")
                                            (str "Ξ"))
                    :expiration    (tf/unparse (tf/formatters :mysql) (web3-utils/web3-time->local-date-time expiration))
                    :asset         :eth
                    :option-type   (case (bn/number option-type)
                                     1 :put
                                     2 :call
                                     :invalid)})}))

(re-frame/reg-event-fx
  ::wrap
  interceptors
  (fn [{:keys [db]} [id]]
(println "dbg wrapping option with id.." id)
    {:dispatch [::tx-events/send-tx
                {:instance (contract-queries/instance db :optionchef)
                 :fn :wrapHegic
                 :args [id]
                 :tx-opts {:from (account-queries/active-account db)}
                 ;; :tx-log {:name tx-log-name :related-href {:name :route/detail :params {:address address}}}
                 :tx-id {:wrap {:hegic id}}
                 :on-tx-success [::wrap-success]
                 :on-tx-error [::logging/error [::wrap]]}]}))

(re-frame/reg-event-fx
  ::wrap-success
  (fn [data] (println "dbg wrapped option ::successfully")))



(re-frame/reg-event-fx
  ::my-hegex-options-count
  interceptors
  (fn [{:keys [db]} _]
    (println "dbg getting my hegex options count")
    {:web3/call
     {:web3 (web3-queries/web3 db)
      :fns [{:instance (contract-queries/instance db :hegexoption)
             :fn :balanceOf
             :args [(account-queries/active-account db)]
             :on-success [::my-hegex-options]
             :on-error [::logging/error [::my-hegex-options-count]]}]}}))


(re-frame/reg-event-fx
  ::my-hegex-options
  interceptors
  (fn [_ [hg-count]]
    (println "dbg hg-count is" hg-count)
    (when hg-count
      {:dispatch-n (mapv (fn [id] [::my-hegex-option id]) (range (bn/number hg-count)))})))


(re-frame/reg-event-fx
  ::my-hegex-option
  interceptors
  (fn [{:keys [db]} [hg-id]]
    {:web3/call
     {:web3 (web3-queries/web3 db)
      :fns [{:instance (contract-queries/instance db :hegexoption)
             :fn :tokenOfOwnerByIndex
             :args [(account-queries/active-account db) hg-id]
             :on-success [::my-hegex-option-success]
             :on-error [::logging/error [::my-hegex-option]]}]}}))

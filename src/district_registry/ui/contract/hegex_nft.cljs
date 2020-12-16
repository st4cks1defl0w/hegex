(ns district-registry.ui.contract.hegex-nft
  (:require
   [bignumber.core :as bn]
   [district.ui.smart-contracts.subs :as contracts-subs]
   [district.ui.web3-accounts.subs :as accounts-subs]
   [re-frame.core :refer [subscribe dispatch]]
   [cljs-web3-next.eth :as web3-ethn]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [web3 :as web3js]
   #_[react :refer [createElement]]
  #_ ["react-dom/server" :as ReactDOMServer :refer [renderToString]]
   [cljs-bean.core :refer [bean ->clj ->js]]
    [cljs-web3.core :as web3]
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
    {:db (assoc-in db [::hegic-options :my :ids] opt-ids)}))

(def deb-owner
  (debounce
   (fn []
     (dispatch [::owner]))
    500))

(defn- ->topic-pad [s]
  (let [Web3 (gget "Web3")
        web3js (Web3. (gget ".?web3.?currentProvider"))]
    (ocall web3js ".?utils.?padLeft" s 64)))

(defn- ->from-topic-pad [s]
  (let [Web3 (gget "Web3")
        web3js (Web3. (gget ".?web3.?currentProvider"))]
    (ocall web3js ".?utils.?hexToNumber" s)))

(def ^:private creation-topic
  "0x9acccf962da4ed9c3db3a1beedb70b0d4c3f6a69c170baca7198a74548b5ef4e")

;; sample address 0xB95Fe51930dDFC546Ff766d59288b50170244B4A
(defn my-hegic-options
  "using up-to-date instance of web3 out of npm [ROPSTEN]"
  [web3-host addr]
  (let [Web3 (gget "Web3")
        web3js (Web3. (gget ".?web3.?currentProvider"))
        _ (ocall! js/window.ethereum "enable")]
    ;; TODO: migrate .getPastLogs call to oops if munged in :advanced
    ;; TODO: add transferred options (pull by transfer topic + receiver)
    (.then (.getPastLogs (oget web3js "eth") #_js/web3.eth.getPastLogs
            (clj->js {:address "0x77041D13e0B9587e0062239d083b51cB6d81404D"
                      :topics [creation-topic,
                               nil,
                               (->topic-pad addr)]
                      :fromBlock 0
                      :toBlock "latest"}))
           (fn [evs]
             (let [ids-raw (map (fn [e] (-> e bean :topics second)) evs)]
               (dispatch [::hegic-options (map ->from-topic-pad ids-raw)]))))))




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

(ns district-registry.ui.trading.events
  (:require
   [re-frame.core :as re-frame :refer [dispatch reg-event-fx]]
    [cljs-web3.core :refer [to-big-number]]
    [oops.core :refer [oget oset! ocall oapply ocall! oapply!
                       gget
                       oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]
   [cljs.core.async :refer [go]]
   [web3 :as web3+]
   [cljs.core.async.interop :refer-macros [<p!]]
   [cljs-bean.core :refer [bean ->clj ->js]]
   ["@0x/connect" :as connect0x]
   ["@0x/web3-wrapper" :as web3-wrapper]
   ["@0x/contract-wrappers" :as contract-wrappers]
   ["@0x/utils" :as utils0x]
   ["@0x/order-utils" :as order-utils0x]
   ["@0x/subproviders" :as subproviders0x]
   [cljs-0x-connect.http-client :as http-client]))

(def interceptors [re-frame/trim-v])

(def ^:private null-address "0x0000000000000000000000000000000000000000")


;; biggest relay, no ropsten
#_(def ^:private apiclient
  (http-client/create-http-client "https://api.radarrelay.com/0x/v3/"))

#_(def ^:private apiclient
  (http-client/create-http-client "https://sra.bamboorelay.com/ropsten/0x/v3/"))


(re-frame/reg-event-fx
  ::create-offer
  interceptors
  (fn [{:keys [db]} [id]]
    (println "dbg WIP creating 0x offer for id" id)))



(def ^:private token-pair
  {:base-token-address "0xe41d2489571d322189246dafa5ebde1f4699f498"
   :quote-token-address "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"})

;; Uncaught (in promise) SyntaxError: JSON.parse: unexpected character at line 1 column 1 of the JSON data
#_(defn- orders-legacy []
;; get-token-pairs-async
  (js-invoke
   (http-client/get-orderbook-async
          apiclient
          #_{:request token-pair}) "then"
         (fn [r]
           (println "orders are" r))))

#_(orders)

;; REWRITE USING js 0x/connect and up-to-date 0x lifecycle

(def ^:private relayer-client
  (let [HttpClient  (oget connect0x "HttpClient")]
    (HttpClient. "http://138.68.106.185:3000/sra/v3/")))


;;hegex dedicated ropsten relay
;;

;; only ropsten relays
;; https://sra.bamboorelay.com/ropsten/0x/v3/
;; https://api-v2.ledgerdex.com/sra/v2/
;; https://api.openrelay.xyz/v2/

;; biggest relay, no ropsten:
;; https://api.radarrelay.com/0x/v3/

(def ^:private decimals 18)

;;TODO
;;swap for ::contract-address sub when out of testing
;;(contract-queries/contract-address db :hegexoption)
(def ^:private nft-address "0x3ea0eab5fc002c0b02842996cbed4ce2e20ee7c5")


;; const zrxTokenAddress = contractWrappers.contractAddresses.zrxToken;
;; const etherTokenAddress = contractWrappers.contractAddresses.etherToken;

;;L0L
;; 0x requires its own BigNumber see:
;; https://github.com/0xProject/0x-monorepo/issues/92#issuecomment-414601020
(defn ->0x-bn [n]
  (new (oget utils0x "BigNumber") n))


;;NOTE
;;won't work until approval to proxy
;;submits a new 0x order in async code golf
(defn order! [hegex-id eth-price]
  ;;placing an order for 1 Hegex
  ;; TODO
  ;; to-big-number *NFT ID*, swap for dynamic
  ;; from user input
  (go
    (let [nft-id (->0x-bn hegex-id)
          Wrapper (oget web3-wrapper "Web3Wrapper")
          ContractWrapper (oget contract-wrappers "ContractWrappers")
          wrapper    (new Wrapper
                          (gget  "web3" ".?currentProvider"))
          contract-wrapper (new ContractWrapper
                                (gget  "web3" ".?currentProvider")
                                (->js {:chainId 3}))
          weth-address (oget contract-wrapper ".?contractAddresses.?etherToken")
          ;; produces the wrong value on ropsten, swap for literal for the time being
          exchange-address  (or  "0xFb2DD2A1366dE37f7241C83d47DA58fd503E2C64"
                                 #_(oget contract-wrapper ".?contractAddresses.?exchange"))
          maker-asset-data (<p! (.callAsync
                                 (.encodeERC721AssetData
                                  (.-devUtils contract-wrapper)
                                  ;;to-bignumber not working here, type mimatch
                                  nft-address
                                  nft-id)))
          taker-asset-data (<p! (.callAsync
                                 (.encodeERC20AssetData
                                  (.-devUtils contract-wrapper)
                                  weth-address)))
          maker-asset-amount  (.toBaseUnitAmount Wrapper
                                                (->0x-bn 1)
                                                 0)
          taker-asset-amount (.toBaseUnitAmount Wrapper
                                                (->0x-bn eth-price)
                                                decimals)
          ;;order expiration stamp of 500 secs from now
          maker-address (first (<p! (ocall wrapper "getAvailableAddressesAsync")))
          expired-at (str (+ 500 (js/Math.floor (/ (js/Date.now) 1000))))
          order-config-request
          ;;kebab ok too
          (->js {:exchangeAddress exchange-address
                 :makerAddress maker-address
                 :takerAddress null-address
                 :expirationTimeSeconds expired-at
                 :makerAssetAmount maker-asset-amount
                 :takerAssetAmount taker-asset-amount
                 :makerAssetData maker-asset-data
                 :takerAssetData taker-asset-data})
          order-config (<p! (ocall relayer-client "getOrderConfigAsync"
                                   order-config-request))
          order (->js (merge
                       {:salt (ocall order-utils0x "generatePseudoRandomSalt")
                        :chainId 3}
                       (->clj order-config-request)
                       (->clj order-config)))
          _ (println "signing order"  order "nft id is" nft-id)
          signed-order (<p! (ocall order-utils0x ".signatureUtils.ecSignOrderAsync"
                                   ;;NOTE
                                   ;;MM/0x subprovider bug workaround
                                   ;;https://forum.0x.org/t/release-0x-js-2-0-0-things-you-need-to-know/207
                                   (new (oget subproviders0x "MetamaskSubprovider")
                                        (gget  "web3" ".?currentProvider"))
                                   order
                                   maker-address))
          ;;DEV
          ;;order ok?
          _order-ok? (println "is order ok?"   (<p! (.callAsync
                                                         (.getOrderRelevantState
                                                          (.-devUtils contract-wrapper)
                                                          signed-order
                                                          (.-signature signed-order)))))]
      (try
        (println "submitted order..."
                 (<p! (ocall relayer-client "submitOrderAsync" signed-order)))
      (catch js/Error err (js/console.log (ex-cause err)))))))


;; request stuff

;; (def ^:private token-pair
;;   {:base-token-address "0xe41d2489571d322189246dafa5ebde1f4699f498"
;;    :quote-token-address "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"})

;; // Taker queries the Orderbook from the Relayer
;; const orderbookRequest: OrderbookRequest = {
;;     baseAssetData: makerAssetData,
;;     quoteAssetData: takerAssetData,
;; };
;; const response = await httpClient.getOrderbookAsync(orderbookRequest);
;; const { bids, asks } = response;

(defn load-orderbook []
  (let [ContractWrapper (oget contract-wrappers "ContractWrappers")
        contract-wrapper (new ContractWrapper
                              (gget  "web3" ".?currentProvider")
                              (->js {:chainId 3}))
        weth-address (oget contract-wrapper ".?contractAddresses.?etherToken")
        #_orderbook-req #_(->js {:base-asset-data })]
    ))


(re-frame/reg-fx
  ::load-orderbook
  (fn []
    (load-orderbook)))


;;REPL functions

;;eval to test order submission on bamboo relay
;;(will sign ok & get rejected for unsupported asset)
;;
#_(order! 1 0.2)




;;then verify orders @ http://138.68.106.185:3000/sra/v3/orders

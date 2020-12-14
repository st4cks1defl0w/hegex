(defproject district0x/hegex "1.0.0"
  :description "District0x-powered synthetic options DEX"
  :url "https://github.com/district0x/hegex"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[camel-snake-kebab "0.4.0"]
                 [binaryage/oops "0.7.0"]
                 [cljs-bean "1.6.0"]
                 [cljs-web3 "0.19.0-0-10"]
                 [cljs-web3-next "0.1.3"]
                 [cljsjs/bignumber "4.1.0-0"]
                 [cljsjs/buffer "5.1.0-1"]
                 [cljsjs/filesaverjs "1.3.3-0"]
                 [cljsjs/react "16.5.2-0"]
                 [cljsjs/react-dom "16.5.2-0"]
                 [cljsjs/recharts "1.6.2-0"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [com.taoensso/encore "2.92.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [day8.re-frame/async-flow-fx "0.1.0"]
                 [district0x/async-helpers "0.1.3"]
                 [district0x/bignumber "1.0.3"]
                 [district0x/cljs-ipfs-native "1.0.1"]
                 [district0x/cljs-solidity-sha3 "1.0.0"]
                 [district0x/district-cljs-utils "1.0.3"]
                 [district0x/district-encryption "1.0.0"]
                 [district0x/district-format "1.0.8"]
                 [district0x/district-graphql-utils "1.0.9"]
                 [district0x/district-parsers "1.0.0"]
                 [district0x/district-sendgrid "1.0.0"]
                 [district0x/district-server-config "1.0.1"]
                 [district0x/district-server-db "1.0.4"]
                 [district0x/district-server-graphql "1.0.15"]
                 [district0x/district-server-logging "1.0.6"]
                 [district0x/district-server-middleware-logging "1.0.0"]
                 [district0x/district-server-smart-contracts "1.2.4"]
                 [district0x/district-server-web3 "1.2.6"]
                 [district0x/district-server-web3-events "1.1.9"]
                 [district0x/district-ui-component-active-account "1.0.0"]
                 [district0x/district-ui-component-active-account-balance "1.0.1"]
                 [district0x/district-ui-component-form "0.2.13"]
                 [district0x/district-ui-component-notification "1.0.0"]
                 [district0x/district-ui-component-tx-button "1.0.0"]
                 [district0x/district-ui-conversion-rates "1.0.1"]
                 [district0x/district-ui-graphql "1.0.9"]
                 [district0x/district-ui-ipfs "1.0.0"]
                 [district0x/district-ui-logging "1.1.0"]
                 [district0x/district-ui-notification "1.0.1"]
                 [district0x/district-ui-now "1.0.1"]
                 [district0x/district-ui-reagent-render "1.0.1"]
                 [district0x/district-ui-router "1.0.5"]
                 [district0x/district-ui-router-google-analytics "1.0.1"]
                 [district0x/district-ui-smart-contracts "1.0.8"]
                 [district0x/district-ui-web3 "1.3.2"]
                 [district0x/district-ui-web3-account-balances "1.0.2"]
                 [district0x/district-ui-web3-accounts "1.0.7"]
                 [district0x/district-ui-web3-balances "1.0.2"]
                 [district0x/district-ui-web3-tx "1.0.12"]
                 [district0x/district-ui-web3-tx-id "1.0.1"]
                 [district0x/district-ui-web3-tx-log "1.0.13"]
                 [district0x/district-ui-window-size "1.0.1"]
                 [district0x/district-web3-utils "1.0.3"]
                 [district0x/eip55 "0.0.1"]
                 [district0x/error-handling "1.0.4"]
                 [district0x/re-frame-ipfs-fx "0.0.2"]
                 [funcool/bide "1.6.1-SNAPSHOT"]
                 [jamesmacaulay/cljs-promises "0.1.0"]
                 [medley "1.0.0"]
                 [mount "0.1.12"]
                 [org.clojure/clojurescript "1.10.764"]
                 [org.clojure/core.async "0.4.490"]
                 [print-foo-cljs "2.0.3"]
                 [re-frame "0.10.5"]
                 [reagent "0.8.1"]
                 #_[district0x/cljs-0x-connect "1.0.0"]]

  :exclusions [express-graphql
               cljsjs/react-with-addons
               org.clojure/core.async
               district0x/async-helpers]

  :plugins [[deraen/lein-less4clj "0.7.0-SNAPSHOT"]
            [cider/cider-nrepl "0.25.2"]
            [lein-auto "0.1.2"]
            [lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.20"]
            [lein-shell "0.5.0"]
            [lein-doo "0.1.8"]
            [lein-pdo "0.1.1"]]

  :doo {:paths {:karma "./node_modules/karma/bin/karma"}}

  :less4clj {:target-path "resources/public/css-compiled"
             :source-paths ["resources/public/css"]}

  :source-paths ["src" "test"]

  :figwheel {:server-port 4177
             :css-dirs ["resources/public/css" "resources/public/css-compiled"]
             :repl-eval-timeout 60000}

  :aliases {"clean-prod-server" ["shell" "rm" "-rf" "server"]
            "watch-css" ["less4clj" "auto"]
            "build-css" ["less4clj" "once"]
            "build-prod-server" ["do" ["clean-prod-server"] ["cljsbuild" "once" "server"]]
            "build-prod-ui" ["do" ["clean"] ["cljsbuild" "once" "ui"]]
            "build-prod" ["pdo" ["build-prod-server"] ["build-prod-ui"] ["build-css"]]
            "build-tests" ["cljsbuild" "once" "server-tests"]
            "test" ["do" ["build-tests"] ["shell" "node" "server-tests/server-tests.js"]]
            "test-doo" ["doo" "node" "server-tests"]
            "test-doo-once" ["doo" "node" "server-tests" "once"]}

  :clean-targets ^{:protect false} [[:solc :build-path]
                                    ".cljs_node_repl"
                                    "dev-server/"
                                    "resources/public/css-compiled/"
                                    "resources/public/js/compiled/"
                                    "server-tests/"
                                    "server/"
                                    "target/"]

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/clojurescript "1.10.439"]
                                  [org.clojure/core.async "0.4.490"]
                                  [binaryage/devtools "0.9.10"]
                                  [cider/piggieback "0.5.2"]
                                  [figwheel-sidecar "0.5.20"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [lein-doo "0.1.8"]
                                  [org.clojure/clojure "1.9.0"]
                                  [org.clojure/tools.reader "1.3.0"]
                                  [re-frisk "0.5.3"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
                   :source-paths ["dev" "src"]
                   :resource-paths ["resources"]}}

  :cljsbuild {:builds [{:id "dev-server"
                        :source-paths ["src/district_registry/server" "src/district_registry/shared"]
                        :figwheel {:on-jsload "district-registry.server.dev/on-jsload"}
                        :compiler {:main "district_registry.server.dev"
                                   :output-to "dev-server/district-registry.js"
                                   :output-dir "dev-server"
                                   :target :nodejs
                                   :optimizations :none
                                   :static-fns true
                                   :fn-invoke-direct true
                                   :anon-fn-naming-policy :mapped
                                   :source-map true}}
                       {:id "dev"
                        :source-paths ["src/district_registry/ui" "src/district_registry/shared"]
                        :figwheel {:on-jsload "district.ui.reagent-render/rerender"}
                        :compiler {:main "district-registry.ui.core"
                                   :output-to "resources/public/js/compiled/app.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :asset-path "/js/compiled/out"
                                   :source-map-timestamp true
                                   :optimizations :none
                                   :npm-deps false
                                   :infer-externs true
                                   :foreign-libs
                                   [{:file "dist/index_bundle.js"
                                     :provides ["web3"]
                                     :global-exports {"web3" Web3}}]
                                   :preloads [print.foo.preloads.devtools
                                              re-frisk.preload]
                                   :external-config {:devtools/config {:features-to-install :all}}}}

                      #_ {:id "dev"
                        :source-paths ["src/district_registry/ui" "src/district_registry/shared"]
                        :figwheel {:on-jsload "district.ui.reagent-render/rerender"}
                        :compiler {:main "district-registry.ui.core"
                                   :output-to "resources/public/js/compiled/app.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :asset-path "/js/compiled/out"
                                   :source-map-timestamp true
                                   :preloads [print.foo.preloads.devtools
                                              re-frisk.preload]
                                   :external-config {:devtools/config {:features-to-install :all}}}}
                       {:id "server"
                        :source-paths ["src"]
                        :compiler {:main "district-registry.server.core"
                                   :output-to "server/district-registry.js"
                                   :output-dir "server"
                                   :target :nodejs
                                   :optimizations :simple
                                   :source-map "server/district-registry.js.map"
                                   :pretty-print false}}
                       {:id "ui"
                        :source-paths ["src"]
                        :compiler {:main "district-registry.ui.core"
                                   :output-to "resources/public/js/compiled/app.js"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :pseudo-names false}}
                       {:id "server-tests"
                        :source-paths ["src/district_registry/server" "src/district_registry/shared" "test/district_registry"]
                        :compiler {:main "district-registry.tests.runner"
                                   :output-to "server-tests/server-tests.js"
                                   :output-dir "server-tests"
                                   :target :nodejs
                                   :optimizations :none
                                   :verbose false
                                   :source-map true}}]})

(ns ^:figwheel-always app.core
    (:require
        [app.util :as util] 
        [reagent.core :as r]
        #_[secretary.core :as secretary :refer-macros [defroute]]
        #_[pushy.core :as pushy]
        [re-com.core   :refer [h-box v-box box gap line scroller border h-split v-split title flex-child-style p]]
        [re-com.splits :refer [hv-split-args-desc]]
        [servant.core :as servant]
        [servant.worker :as worker]
        [cljs.core.async :as a :refer [<! >!]]
        [cljs.reader :refer [read-string]]
        )
    (:require-macros
     [servant.macros :refer [defservantfn]]
     [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defonce grammar (r/atom "
<S> = (sexp | whitespace)+
sexp = <'('> operation <')'>

<operation> = operator + args
operator = #'[+*-\\\\]'
args = (num | <whitespace> | sexp)+
<num> = #'[0-9]+'
<whitespace> = #'\\s+'"))

(defonce sample (r/atom "(+ 1 (- 3 1))"))

(defonce parse-output (r/atom ""))

(defonce updated-text (a/chan))

(defn parsed-output []
  [:div
   {:id "parsed-output"
    :style {:overflow-y "auto"}}
   @parse-output])

 (defn app []
   [h-split
    :height "100%"
    :margin "0"
    :initial-split "65"
    :style {:background "#e0e0e0"}
    :panel-1 [v-split
              :margin "30px 10%"
              :initial-split "25"
              :panel-1 [v-box
                        :size "1"
                        :children [[:div
                                    {:style {:marginBottom 10 :textAlign "center"}}
                                    "Build a parser in your browser! This page runs the " [:a {:href "https://github.com/lbradstreet/instaparse-cljs"} "ClojureScript port"]
                                    " of " [:a {:href "https://github.com/Engelberg/instaparse"} "Instaparse"] "."]
                                   [util/cm-editor sample {:theme "solarized light"}]]]
              :panel-2 [parsed-output]
     ]
    :panel-2 [v-box
              :size "1"
              :children [[util/cm-editor grammar]]]
    ])

(defservantfn worker-parse
  [grammar input]
  (let [result (pr-str (util/parse grammar input))]
    (prn "Sending back result:" result)
    result))

(defn init []
  (r/render-component [app] (.getElementById js/document "app"))
  (def servant-channel (servant/spawn-servants 2 "js/compiled/app.js"))
  (go-loop [grammar1 nil
            sample1 nil
            wait? false]
    (when wait?
      (<! updated-text))
    (let [grammar2 @grammar
          sample2 @sample]
      (if (and (= grammar1 grammar2)
               (= sample1 sample2))
        (recur grammar1 sample1 true)
        (do (let [parse-result (servant/servant-thread servant-channel
                                                       servant/standard-message
                                                       worker-parse
                                                       grammar2
                                                       sample2)]
              (prn "Ready to receive parse result")
              (let [result (<! parse-result)
                    _ (prn "Got parse result!" result)
                    edn-parsed (read-string result)]
                (prn "Parsed edn:" edn-parsed)
                (reset! parse-output edn-parsed)))
            (recur grammar2 sample2 true)))))
  (doseq [a [grammar sample]]
    (add-watch a ::listener
               (fn [k a old new]
                 (prn "Updated!" k a old new)
                 (go (>! updated-text true))))))

(if (servant/webworker?)
  (worker/bootstrap)
  (init))

 (defn on-js-reload []
    ;; optionally touch your app-state to force rerendering depending on 
    ;; your application
    ;; (swap! app-state update-in [:__figwheel_counter] inc)
    )
 

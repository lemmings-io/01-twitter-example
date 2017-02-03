(ns twitter-example.core
 (:require [clojure.set]
           [clojure.string]
           [clj-http.client :as client]
           [overtone.at-at :as overtone]
           [twitter.api.restful :as twitter]
           [twitter.oauth :as twitter-oauth]
           [environ.core :refer [env]]))

(defn word-chain [word-transition]
  (reduce (fn [r t] (merge-with clojure.set/union r
                                (let [[a b c] t]
                                  {[a b] (if c #{c} #{})})))
          {}
          word-transition))

(defn text->word-chain [s]
  (let [words (clojure.string/split s #"[\s|\n]")
        word-transitions (partition-all 3 1 words)]
    (word-chain word-transitions)))

(defn chain->text [chain]
  (apply str (interpose " " chain)))

(defn walk-chain [prefix chain result]
  (let [suffixes (get chain prefix)]
    (if (empty? suffixes)
     result
     (let [suffix (first (shuffle suffixes))
           new-prefix [(last prefix) suffix]
           result-with-spaces (chain->text result)
           result-char-count (count result-with-spaces)
           suffix-char-count (+ 1 (count suffix))
           new-result-char-count (+ result-char-count suffix-char-count)]
      (if (>= new-result-char-count 140)
       result
       (recur new-prefix chain (conj result suffix)))))))

(defn generate-text
  [start-phrase word-chain]
  (let [prefix (clojure.string/split start-phrase #" ")
        result-chain (walk-chain prefix word-chain prefix)
        result-text (chain->text result-chain)]
   result-text))

(defn process-file [fname]
  (text->word-chain
    (slurp (clojure.java.io/resource fname))))

(def files ["fluffyrocketship2.txt" "fluffyrocketship.txt" "astronomy.txt" "theguide.txt"])
(def functional-leary (apply merge-with clojure.set/union (map process-file files)))

(def prefix-list ["I will" "But if" "So I" "I was" "I am" "We also"
                  "We have" "I can" "I never" "For you"
                  "We need" "We will" "Now this"
                  "You might" "This is" "Let me" "You better"])

(defn end-at-last-punctuation [text]
  (let [trimmed-to-last-punct (apply str (re-seq #"[\s\w]+[^.!?,]*[.!?,]" text))
        trimmed-to-last-word (apply str (re-seq #".*[^a-zA-Z]+" text))
        result-text (if (empty? trimmed-to-last-punct)
                      trimmed-to-last-word
                      trimmed-to-last-punct)
        cleaned-text (clojure.string/replace result-text #"[,| ]$" ".")]
    (clojure.string/replace cleaned-text #"\"" "'")))

(defn tweet-text  []
  (let [text (generate-text (-> prefix-list shuffle first) functional-leary)]
    (end-at-last-punctuation text)))

(def my-creds (twitter-oauth/make-oauth-creds (env :app-consumer-key)
                                              (env :app-consumer-secret)
                                              (env :user-access-token)
                                              (env :user-access-secret)))

(defn status-update []
  (let [tweet (tweet-text)]
    (println "generate tweet is :" tweet)
    (println "char count is:" (count tweet))
    (when (not-empty tweet)
      (try (twitter/statuses-update :oauth-creds my-creds
                                  :params {:status tweet})
           (catch Exception e (println "Oh no! " (.getMessage e)))))))

(def my-pool (overtone/mk-pool))

(defn -main [& args]
  ;; every 2 hours
  (println "Started up")
  (println (tweet-text))
  (overtone/every (* 1000 60 60 2) #(println (status-update)) my-pool))

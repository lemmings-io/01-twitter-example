(ns twitter-example.core
 (:require [clojure.set]
           [clojure.string :as s]
           [clj-http.client :as client]
           [overtone.at-at :as overtone]
           [twitter.api.restful :as twitter]
           [twitter.oauth :as twitter-oauth]
           [environ.core :refer [env]]))

; We are generating tweets based on templates, similar to the game Apples to
; Apples: https://en.wikipedia.org/wiki/Apples_to_Apples
; We start with two lists of strings: One list contains string with blank
; spaces, the other list is used to fill in these spaces.


; We define the "templates" - these strings are the skeleton of the tweet
; We will later replace every occurence of ___ with a string that we chose
; randomly from the list "blanks".
(def templates [
                "___ sent a card to ___."])

; Next we define the "blanks"
(def blanks ["a haunted house"
             "Rich Hickey"
             "a purple dinosaur"
             "junk mail"
             "pirates"])

; We are using build-sentence in generate-sentence, so we need to declare it
; since we define it only afterwards
(declare build-sentence)

; generate-sentence returns a random sentence, built by choosing one template
; string at random and filling in each blank space (___) with a randomly chosen
; string from the blanks list.
; Implementation: We take a random template string, and prepare the available
; blanks by shuffling them. We then hand over to build-sentence.
(defn generate-sentence []
  (build-sentence (rand-nth templates) (shuffle blanks)))

; build-sentence is used by generate-sentence to build up a sentence from a
; template step by step.
; Starting with the template given to us by generate-sentence we replace
; one occurence of ___ with the first strings in blanks. Next, we check
; if there are more ___ remaining, if so we do it again. Otherwise we're done.
(defn build-sentence [template blanks]
  (let [blank (first blanks)
        remaining-blanks (rest blanks)
        sentence (s/replace-first template "___" blank)]
      (if (s/includes? sentence "___")
        (recur sentence remaining-blanks)
        sentence)))

; We retrieve the twitter credentials from the profiles.clj file here.
; In profiles.clj we defined the "env(ironment)" which we use here
; to get the secret passwords we need.
; Make sure you add your credentials in profiles.clj and not here!
(def twitter-credentials (twitter-oauth/make-oauth-creds (env :app-consumer-key)
                                                         (env :app-consumer-secret)
                                                         (env :user-access-token)
                                                         (env :user-access-secret)))

; Tweets are limited to 140 characters. We might randomly generate a sentence
; with more than 140 characters, which would be rejected by Twitter.
; So we check if our generated sentence is longer than 140 characters, and
; don't tweet if so.
(defn tweet [text]
  (when (and (not-empty text) (<= (count text) 140))
   (try (twitter/statuses-update :oauth-creds twitter-credentials
                                 :params {:status text})
        (catch Exception e (println "Something went wrong: " (.getMessage e))))))

; Generate a sentence and tweet it.
(defn tweet-sentence []
  (tweet (generate-sentence)))


(def my-pool (overtone/mk-pool))

(defn -main [& args]
  ;; every 2 hours
  (println "Started up")
  (println (tweet-sentence))
  (overtone/every (* 1000 60 60 2) #(println (tweet-sentence)) my-pool))

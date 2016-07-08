(ns three-in-a-row.core
  (:require [clojure
             [spec :as s]
             [string :as str]]
            [net.cgrand.enlive-html :as h]
            [ring.middleware
             [params :refer [wrap-params]]
             [session :refer [wrap-session]]]
            [ring.util.response :as response]))

;; Specs

(s/def ::pos (s/and int? #(<= 0 % 8)))
(s/def ::mark #{:x :o})
(s/def ::place-action (s/keys :req [::pos ::mark]))
(s/def ::restart true?)
(s/def ::restart-action (s/keys :req [::restart]))

(defn get-action-type
  [action-map]
  (if (::restart action-map) :restart :place))

(defmulti action-type get-action-type)
(defmethod action-type :place [_] ::place-action)
(defmethod action-type :restart [_] ::restart-action)

(s/def ::action (s/multi-spec action-type :action-type))


;; Game logic

(def empty-board (vec (repeat 9 nil)))

(defn turn
  "Returns who has the next turn"
  [board]
  (let [{:keys [x o] :or {x 0 o 0}} (frequencies board)]
    (if (> x o) :o :x)))

(defn- all-same
  [seq]
  (let [freqs (frequencies seq)]
    (when (= (count freqs) 1)
      (first (keys freqs)))))

(defn- project-indices
  [board indices]
  (map #(nth board %) indices))

(defn winning-sequence
  [board indices]
  (all-same (project-indices board indices)))

(defn winner
  [board]
  (or (winning-sequence board [0 1 2])
      (winning-sequence board [3 4 5])
      (winning-sequence board [6 7 8])
      (winning-sequence board [0 3 6])
      (winning-sequence board [1 4 7])
      (winning-sequence board [2 5 8])
      (winning-sequence board [0 4 8])
      (winning-sequence board [2 4 6])
      (when (every? (complement nil?) board) :draw)))

(defn valid-place? [board pos mark]
  (and (= (turn board) mark)
       (nil? (board pos))
       (not (winner board))))

(defmulti perform-action (fn [board action] (get-action-type action)))

(defmethod perform-action :place [board action]
  {:pre [(s/valid? ::place-action action)]}
  (let [{pos ::pos mark ::mark} action]
    (if (valid-place? board pos mark)
      (assoc board pos mark)
      board)))

(defmethod perform-action :restart [_ action]
  {:pre [(s/valid? ::restart-action action)]}
  empty-board)


;; Query params

(defn ->int
  [s]
  (when-let [s (re-find #"\d+" s)]
    (Integer. s)))

(defn pos-from-query-param
  [params]
  (when-let [pos-str (params "pos")]
    (when-let [pos (->int pos-str)]
      (when (s/valid? ::pos pos)
        pos))))

(defn mark-from-query-param
  [params]
  (when-let [mark-str (params "mark")]
    (let [mark (keyword mark-str)]
      (when (s/valid? ::mark mark)
        mark))))

(defn get-action
  "Returns action (or nil) from query params"
  [params]
  {:post [(or (s/valid? ::action %) (nil? %))]}
  (let [pos (pos-from-query-param params)
        mark (mark-from-query-param params)
        restart (= (params "restart") "true")]
    (cond
      restart {::restart restart}
      (and pos mark) {::pos pos ::mark mark}
      :else nil)))


;; Template

(defn mark->str
  [mark]
  (when mark
    (str/upper-case (name mark))))

(defn- end-message
  [board]
  (if-let [winner (winner board)]
    (if (= winner :draw)
      "Draw game"
      (str (mark->str winner) " wins!"))))

(h/deftemplate index "index.html"
  [board winner]
  [:table :tr]
  (h/clone-for [row (partition 3 board)]
               [:tr :td]
               (h/clone-for [mark row]
                            [:td] (h/content (mark->str mark))
                            [:td] (h/add-class (when mark "occupied"))))
  [:#message] (h/content (end-message board)))


(defn handler
  [{session :session params :params}]
  (let [board (:board session empty-board)
        board (if-let [action (get-action params)]
                (perform-action board action)
                board)
        winner (winner board)
        session (assoc session :board board)]
    (-> (response/response (index board winner))
        (assoc :session session)
        (response/content-type "text/html"))))

(def app
  (-> handler
      wrap-params
      wrap-session))

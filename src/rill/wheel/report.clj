(ns rill.wheel.report
  "Tools for reporting on aggregates, events and commands"
  (:require [clojure.string :as string]))

(defn- vars-with-meta
  [ns k]
  (filter #(get (meta %) k)
          (vals (ns-publics ns))))

(defn ns-events
  [ns]
  (vars-with-meta ns :rill.wheel/event-fn))

(defn events
  []
  (mapcat ns-events (all-ns)))

(defn ns-commands
  [ns]
  (vars-with-meta ns :rill.wheel/command-fn))

(defn commands
  []
  (mapcat ns-commands (all-ns)))

(defn ns-aggregates
  [ns]
  (vars-with-meta ns :rill.wheel/descriptor-fn))

(defn aggregates
  []
  (mapcat ns-aggregates (all-ns)))

(defn keyword->sym
  [k]
  (symbol (subs (str k) 1)))

(defn- wheel-type
  [m]
  (if (var? m)
    (wheel-type (meta m))
    (keyword (-> m :ns ns-name name) (-> m :name name))))

(defn- report-map
  [vars]
  (into {} (map (fn [v]
                  [(wheel-type v) (meta v)])
                vars)))


(defn assoc-events
  [a-map]
  (reduce (fn [a e]
            (assoc-in a [(:rill.wheel/aggregate (meta e)) :rill.wheel/events (wheel-type e)]
                      (meta e)))
          a-map
          (events)))

(defn assoc-commands
  [a-map]
  (reduce (fn [a e]
            (assoc-in a [(:rill.wheel/aggregate (meta e)) :rill.wheel/commands (wheel-type e)]
                      (meta e)))
          a-map
          (commands)))

(defn report-data
  "Map with metadata for all aggregates, including events and commands"
  []
  (-> (report-map (aggregates))
      (assoc-events)
      (assoc-commands)))

(defn- key->title
  [k]
  (let [words (string/split (name k) #"-")]
    (string/join " " (map string/capitalize words))))

(defn- line-indent [line]
  (count (take-while #{\space} line)))

(defn- find-min-indent [lines]
  (if-let [indents (->> lines
                        (remove string/blank?)
                        (map line-indent)
                        seq)]
    (reduce min Long/MAX_VALUE indents)
    0))

(defn- remove-indent [line indentation]
  (subs line (min indentation (count line))))

(defn- remove-extraneous-indentation [doc]
  (when doc
    (let [[first-line & lines] (string/split doc #"\n")
          indentation          (find-min-indent lines)]
      (->> lines
           (map #(remove-indent % indentation))
           (cons first-line)
           (string/join "\n")))))

(defn- propslist
  [s]
  (when-let [l (seq (map (fn [p]
                           (str "  - " (key->title p) "\n"))
                         s))]
    (concat ["**Properties**\n\n"]
            l
            ["\n"])))

(defn- msglist
  [m]
  (->> (sort (keys m))
       (mapcat (fn [k]
              (let [e (get m k)]
                (-> ["### " (key->title k) "\n\n"
                     "    " k "\n\n"
                     (remove-extraneous-indentation (:doc e))
                     "\n\n"]
                    (into (propslist (:rill.wheel/properties e)))
                    (conj "\n\n")))))
       (apply str)))

(defn report-data->markdown
  [r]
  (->> (sort (keys r))
       (mapcat (fn [k]
                 (let [m (get r k)]
                   (-> ["# " (key->title k) "\n\n"
                        "    " k "\n\n"
                        (remove-extraneous-indentation (:doc m))
                        "\n\n"]
                       (into (propslist (:rill.wheel/properties m)))
                       (conj "## Events\n\n")
                       (into (msglist (:rill.wheel/events m)))
                       (into ["\n\n"
                              "## Commands\n\n"])
                       (into (msglist (:rill.wheel/commands m)))))))
       (apply str "# Model overview\n\n")))

(defn report
  "Markdown formatted report of all aggregates, events and commands in
  the system."
  []
  (-> (report-data)
      (report-data->markdown)))


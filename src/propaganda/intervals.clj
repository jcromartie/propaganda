(ns propaganda.intervals
  (:require [propaganda.core :as propaganda]
            [propaganda.generic-operators :as go]))

;; Interval arithmetics

(defrecord Interval
    [lo hi])

(defn make-interval
  [lo hi]
  (Interval. lo hi))

(defn mul-interval
  "Multiplies the intervals. Assumes all limits are positive."
  [x y]
  (make-interval (* (:lo x) (:lo y)) (* (:hi x) (:hi y))))

(defn div-interval
  "Divides the intervals. Assumes all limits are strictly positive."
  [x y]
  (mul-interval x (make-interval (/ 1 (:hi y)) (/ 1 (:lo y)))))

(defn square-interval
  [x]
  (make-interval (* (:lo x) (:lo x))
                 (* (:hi x) (:hi x))))

(defn sqrt-interval
  [x]
  (make-interval (Math/sqrt (double (:lo x)))
                 (Math/sqrt (double (:hi x)))))

(defn empty-interval?
  [x]
  (> (:lo x) (:hi x)))

(defn intersect-intervals
  [x y]
  (make-interval
   (max (:lo x) (:lo y))
   (min (:hi x) (:hi y))))

(defn interval?
  [x]
  (isa? (class x) Interval))

(defn ensure-inside
  [interval number]
  (if (<= (:lo interval) number (:hi interval))
    number
    (propaganda/contradiction
     (str number " not in interval [" (:lo interval?) ", " (:hi interval) "]"))))

;; Extend supplied merge

(defn extend-merge
  "Extends the supplied generic operator with interval mergings. These
  also include merging numbers with intervals."
  [generic-merge-operator]
  (doto generic-merge-operator
    (go/assign-operation (fn [content increment]
                           (let [new-range (intersect-intervals content increment)]
                             (if (empty-interval? new-range)
                               (propaganda/contradiction
                                (str "Non-overlapping intervals: " content " and " increment))
                               new-range)))
                         interval? interval?)
    (go/assign-operation (fn [content increment]
                           (ensure-inside increment content))
                         number? interval?)
    (go/assign-operation (fn [content increment]
                           (ensure-inside content increment))
                         interval? number?)))

;; Generic standard arithmetic operations

(defn ->interval
  [x]
  (if (interval? x)
    x
    (make-interval x x)))

(defn coercing
  [coercer f]
  (fn [& args]
    (apply f (map coercer args))))

(def generic-mul (doto (go/generic-operator *)
                   (go/assign-operation mul-interval
                                        interval? interval?)
                   (go/assign-operation (coercing ->interval mul-interval)
                                        number? interval?)
                   (go/assign-operation (coercing ->interval mul-interval)
                                        interval? number?)))
(def generic-div (doto (go/generic-operator /)
                   (go/assign-operation div-interval
                                        interval? interval?)
                   (go/assign-operation (coercing ->interval div-interval)
                                        number? interval?)
                   (go/assign-operation (coercing ->interval div-interval)
                                        interval? number?)))
(def generic-square (doto (go/generic-operator (fn [x] (* x x)))
                      (go/assign-operation
                       square-interval
                       interval?)))
(def generic-sqrt (doto (go/generic-operator (fn [x] (Math/sqrt (double x))))
                    (go/assign-operation
                     sqrt-interval
                     interval?)))

;; Propagator constructors

(def multiplier (propaganda/function->propagator-constructor generic-mul))
(def divider    (propaganda/function->propagator-constructor generic-div))
(def squarer    (propaganda/function->propagator-constructor generic-square))
(def sqrter     (propaganda/function->propagator-constructor generic-sqrt))

;; Multidirectional propagators constructors (relations)

(defn product
  "Creates the product relation x * y = total between the cells."
  [x y total]
  (multiplier x y total)
  (divider total x y)
  (divider total y x))

(defn quadratic
  "Creates the quadratic relation x * x = x-squared between the cells."
  [x x-squared]
  (squarer x x-squared)
  (sqrter x-squared x))
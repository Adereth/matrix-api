(ns clojure.core.matrix.impl.wrappers
  (:require [clojure.core.matrix.protocols :as mp])
  (:use clojure.core.matrix.utils)
  (:require [clojure.core.matrix.implementations :as imp])
  (:require [clojure.core.matrix.multimethods :as mm]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

(declare wrap-slice)
(declare wrap-nd)

;; =============================================
;; ScalarWrapper
;;
;; wraps a single scalar as a mutable 0-D array

(deftype ScalarWrapper [^{:volatile-mutable true} value]
  java.lang.Object
    (toString [m] (str value))
       
  mp/PImplementation
    (implementation-key [m] 
      :scalar-wrapper)
    ;; we delegate to persistent-vector implementation for new matrices.
    (new-vector [m length] 
      (mp/new-vector [] length))
    (new-matrix [m rows columns] 
      (mp/new-matrix [] rows columns))
    (new-matrix-nd [m dims] 
      (mp/new-matrix-nd [] dims))
    (construct-matrix [m data]
      (if (mp/is-scalar? m)
        (ScalarWrapper. m)
        (ScalarWrapper. (mp/get-0d m))))
    (supports-dimensionality? [m dims] 
      (== dims 0))
    
  mp/PDimensionInfo
    (dimensionality [m]
      0)
    (get-shape [m]
      [])
    (is-scalar? [m]
      false)
    (is-vector? [m]
      false)
    (dimension-count [m dimension-number]
      (error "Can't get dimension-count of ScalarWrapper: no dimensions exist"))
   
  mp/PIndexedAccess
    (get-1d [m row]
      (error "Can't get-1d on ScalarWrapper."))
    (get-2d [m row column]
      (error "Can't get-2d on ScalarWrapper."))
    (get-nd [m indexes]
      (if (seq indexes)
        (error "Can't get-1d on ScalarWrapper.")
        value))
    
  mp/PIndexedSetting
    (set-1d [m x v]
      (error "Can't do 1D set on 0D array"))
    (set-2d [m x y v]
      (error "Can't do 2D set on 0D array"))
    (set-nd [m indexes v]
      (if (not (seq indexes)) 
        (ScalarWrapper. v)
        (error "Can't set on 0D array with dimensionality: " (count indexes))))
    (is-mutable? [m] true)
    
  ;; in nested vector format, we don't want the wrapper....  
  mp/PConversion
    (convert-to-nested-vectors [m]
      value)
    
  mp/PZeroDimensionAccess
    (get-0d [m]
      value)
    (set-0d! [m v]
      (set! value v))
    
  mp/PMatrixCloning
    (clone [m] (ScalarWrapper. value)))

;; =============================================
;; SliceWrapper
;;
;; wraps a row-major slice of an array

(deftype SliceWrapper [array ^long slice]
  mp/PImplementation
    (implementation-key [m] 
      :slice-wrapper)
    ;; we delegate to persistent-vector implementation for new matrices.
    (new-vector [m length] 
      (mp/new-vector [] length))
    (new-matrix [m rows columns] 
      (mp/new-matrix [] rows columns))
    (new-matrix-nd [m dims] 
      (mp/new-matrix-nd [] dims))
    (construct-matrix [m data]
      (mp/construct-matrix [] data))
    (supports-dimensionality? [m dims] 
      true)
    
  mp/PDimensionInfo
    (dimensionality [m]
      (dec (mp/dimensionality array)))
    (get-shape [m]
      (next (mp/get-shape array)))
    (is-scalar? [m]
      false)
    (is-vector? [m]
      (== 2 (mp/dimensionality array)))
    (dimension-count [m dimension-number]
      (mp/dimension-count array (inc dimension-number)))
   
  mp/PIndexedAccess
    (get-1d [m row]
      (mp/get-2d array slice row))
    (get-2d [m row column]
      (mp/get-nd array [slice row column]))
    (get-nd [m indexes]
      (mp/get-nd array (cons slice indexes)))
    
  mp/PZeroDimensionAccess
    (get-0d [m]
      (mp/get-1d array slice))
    (set-0d! [m value]
      (mp/set-1d array slice value))
    
  mp/PConversion
    (convert-to-nested-vectors [m]
      (if (mp/is-vector? array) 
        (mp/get-1d array slice)
        (mapv mp/convert-to-nested-vectors (mp/get-major-slice-seq m))))

  mp/PIndexedSetting
    (set-1d [m row v]
      (let [m (mp/clone m)]
        (mp/set-1d! m row v)
        m))
    (set-2d [m row column v]
      (let [m (mp/clone m)]
        (mp/set-2d! m row column v)
        m))
    (set-nd [m indexes v]
      (let [m (mp/clone m)]
        (mp/set-nd! m indexes v)
        m))
    (is-mutable? [m]
      (mp/is-mutable? array))
    
  mp/PIndexedSettingMutable
    (set-1d! [m row v]
      (mp/set-2d! array slice row v))
    (set-2d! [m row column v]
      (mp/set-nd! array [slice row column] v))
    (set-nd! [m indexes v]
      (mp/set-nd! array (cons slice indexes) v))
    
  mp/PMatrixCloning
    (clone [m] (wrap-slice (mp/clone array) slice)))


;; =============================================
;; NDWrapper
;;
;; wraps an N-dimensional subset or broadcast of an array
;; supports aritrary permutations of dimensions and indexes

(deftype NDWrapper
  [array ;; source array
   ^longs shape ;; shape of NDWrapper
   ^longs dim-map ;; map of NDWrapper dimensions to source dimensions
   ^objects index-maps ;; maps of each NDWrapper dimension's indexes to source dimension indexes
   ^longs source-position ;; position in source array for non-specified dimensions
   ]
   mp/PImplementation
    (implementation-key [m] 
      :nd-wrapper)
    ;; we delegate to persistent-vector implementation for new matrices.
    (new-vector [m length] 
      (mp/new-vector [] length))
    (new-matrix [m rows columns] 
      (mp/new-matrix [] rows columns))
    (new-matrix-nd [m dims] 
      (mp/new-matrix-nd [] dims))
    (construct-matrix [m data]
      (mp/construct-matrix [] data))
    (supports-dimensionality? [m dims] 
      true)
    
  mp/PIndexedSetting
    (set-1d [m x v]
      (TODO))
    (set-2d [m x y v]
      (TODO))
    (set-nd [m indexes v]
      (TODO))
    (is-mutable? [m] (mp/is-mutable? array)) 
    
  mp/PDimensionInfo
    (dimensionality [m]
      (alength shape))
    (get-shape [m]
      shape)
    (is-scalar? [m]
      false)
    (is-vector? [m]
      (== 1 (alength shape)))
    (dimension-count [m dimension-number]
      (aget shape (int dimension-number)))
    
  mp/PZeroDimensionAccess
    (get-0d [m]
      (mp/get-nd array source-position))
    (set-0d! [m value]
      (mp/set-nd array source-position value))
    
  mp/PIndexedAccess
    (get-1d [m row]
      (let [ix (copy-long-array source-position)
            ^longs im (aget index-maps 0)]
        (aset ix (aget dim-map 0) (aget im row))
        (mp/get-nd array ix)))
    (get-2d [m row column]
      (let [ix (copy-long-array source-position)]
        (aset ix (aget dim-map 0) (aget ^longs (aget index-maps 0) row))
        (aset ix (aget dim-map 1) (aget ^longs (aget index-maps 0) column))
        (mp/get-nd array ix)))
    (get-nd [m indexes]
      (let [^longs ix (copy-long-array source-position)
            ^longs im (aget index-maps 0)]
        (dotimes [i (alength shape)]
          (aset ix (aget dim-map i) (aget ^longs (aget index-maps i) (nth indexes i))))
        (mp/get-nd array ix))))

(defn wrap-slice 
  "Creates a view of a major slice of an array."
  ([m slice]
    (let [slice (long slice)]
      ;; (assert (> (mp/dimensionality m) 0))
      ;; (assert (> (mp/dimension-count m 0) slice -1))
      (SliceWrapper. m slice))))

(defn wrap-nd 
  "Wraps an array in a view. Good for taking submatrices, subviews etc."
  ([m]
	  (let [shp (long-array (mp/get-shape m))
	        dims (alength shp)]
	    (NDWrapper. m 
	              shp
	              (long-range dims)
	              (object-array (map #(long-range (mp/dimension-count m %)) (range dims)))
	              (long-array (repeat dims 0))))))

(defn wrap-scalar 
  "Wraps a scalar value into a mutable 0D array."
  ([m]
	  (cond 
	    (mp/is-scalar? m)
	      (ScalarWrapper. m)
	    :else 
	      (ScalarWrapper. (mp/get-0d m)))))

(imp/register-implementation (NDWrapper. nil nil nil nil nil))

(imp/register-implementation (wrap-slice [1 2] 0))

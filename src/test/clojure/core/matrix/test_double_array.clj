(ns core.matrix.test-double-array
  (:use clojure.test)
  (:use core.matrix)
  (:require [core.matrix.operators :as op])
  (:require [core.matrix.compliance-tester])
  (:require core.matrix.impl.double-array))

(deftest test-create
  (testing "making a double array"
    (let [da (matrix :double-array [1 2])]
      (is (= [1.0 2.0] (seq da)))
      (is (= (class (double-array [1])) (class da))))))
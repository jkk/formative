# Formative

A Clojure library for dealing with web forms.

Does three things, given a form specification:

* Renders a form via pluggable renderers. Comes with [Hiccup](https://github.com/weavejester/hiccup) renderers for Bootstrap and for a table-based layout
* Parses form data from Ring params
* Validates parsed data using [Verily](https://github.com/jkk/verily)

## Installation

Leiningen coordinate:

```clj
[formative "0.2.1"]
```

## Usage


The important namespaces are `formative.core` and `formative.parse`.

```clj
(ns example.core
  (:require [formative.core :as f]
            [formative.parse :as fp]))
```

### Building a Form

To build a form, you need a form specification, which is a map that looks like this:

```clj
(def example-form
  {:method :post
   :fields [{:name :secret-code :type :hidden :datatype :int}
            {:name :email :type :email}
            {:name :password :type :password}
            {:name :remember :type :checkbox}]
   :validations [[:required [:secret-code :email :password]]
                 [:min-length 8 :password]]
   :values {:secret-code 1234
            :remember true}})
```

The map can contain keys such as `:method` and `:action` that directly correspond to HTML form attributes. It can also contain special keys such as `:fields` and `:submit-label`. See [Form and Field Specifications](#form-and-field-specifications) for more about the specification format.

### Rendering a Form

Render a form using `formative.core/render-form`:

```clj
(f/render-form example-form)
;; Returns:
[:div {:class "form-shell form-horizontal bootstrap-form"}
 [:form {:method :post}
  ;; ... etc ...
  ]]
```

Using the default `:bootstrap-horizontal` renderer and a [Bootstrap](http://twitter.github.com/bootstrap/) theme, the form will look something like this:

![form](https://github.com/jkk/formative/raw/master/doc/bootstrap-horizontal.png)

Note: Formative does not include Bootstrap itself or any styling. You are responsible for providing CSS, images, etc.

You can also render individual fields using `formative.core/render-field`. Unlike `render-form`, `render-field` _always_ returns Hiccup data. `render-field` takes a field specification and an optional value:

```clj
(f/render-field {:name :flavor
                 :type :select
                 :options ["Chocolate" "Vanilla" "Strawberry"]}
                "Vanilla")
;; Returns:
[:select {:name "flavor"}
 ([:option {:value "Chocolate", :selected false} "Chocolate"]
  [:option {:value "Vanilla", :selected true} "Vanilla"]
  [:option {:value "Strawberry", :selected false} "Strawberry"])]
```

Notice that the "Vanilla" option is selected in our generated element.

All of the built-in form renderers make use of `render-field`, but not all renderers are required to do so.

Custom form renderers can be implemented using the `formative.render-form/render-form*` multimethod.

### Parsing Form Data

`formative.parse/parse-params` will turn a form specification and a [Ring](https://github.com/ring-clojure/ring) params map into a map of parsed form data. It will parse each field according to its `:type` or `:datatype` keys.

```clj
(fp/parse-params example-form
                 {:secret-code "1234"
                  :email "foobar@gmail.com"
                  :password "barbazquux"
                  :remember "false"})
;; Returns:
{:remember false, :secret-code 1234, :password "barbazquux", :email "foobar@gmail.com"}
```

Notice that the `:remember` and `:secret-code` fields have been parsed from strings into their respective datatypes.

By default, validation will be performed on the parsed values and an exception thrown if validation fails. If the `:validate` keyword option to `parse-params` is `false`, `ParseError` records will be used in place of values that failed to parse, and no validation will be done.

Any exception thrown due to a failed parse or validation will contain a `:problems` `ex-data` key with information about the failed exception.

```clj
(fp/parse-params example-form {:secret-code "xxx"})
;; ExceptionInfo Problem parsing params  clojure.core/ex-info (core.clj:4227)

(ex-data *e)
;; {:problems ({:keys (:secret-code), :msg "must be a number"}), …}

(fp/parse-params example-form {:secret-code "xxx"} :validate false)
{:secret-code #formative.parse.ParseError{}}
```

The `formative.parse/with-fallback` macro is a handy way to try parsing Ring params, and call a "fallback" function when it fails. The fallback function will be supplied the problems as its argument.

```clj
(defn submit-example-form [params]
  ;; Calls (show-example-form params :problems problems) if parsing fails
  (fp/with-fallback (partial show-example-form params :problems)
    (let [values (fp/parse-params example-form params))]
      ;; Success
      (prn-str values)))

```

### Validating Parsed Data

Formative uses [Verily](https://github.com/jkk/verily) to validate parsed data. By default, only datatypes are validated. There are two ways to add your own validation to a form: `:validations` and `:validator`.

#### `:validations`

A sequence of validation specifications. For example:

```
[[:required [:foo :bar :password]]
 [:equal [:password :confirm-password] "Passwords don't match, dummy"]
 [:min-length 8 :password]]
```

All validation specifications accept a key or sequence of keys. The message is always optional. Unless `:required` is used, all validations allow `nil` or blank.

Built in validations:

* `:required <keys> [msg]` - must not be blank or nil
* `:contains <keys> [msg]` - can be blank or nil but must be present in the values map
* `:exact <value> <keys> [msg]` - must be a particular value
* `:equal <keys> [msg]` - all keys must be equal
* `:email <keys> [msg]` - must be a valid email
* `:matches <regex> <keys> [msg]` - must match a regular expression
* `:min-length <len> <keys> [msg]` - must be a certain length (for strings or collections)
* `:max-length <len> <keys> [msg]` - must not exceed a certain length (for strings or collections)
* `:min-val <min> <keys> [msg]` - must be at least a certain value
* `:max-val <max> <keys> [msg]` - must be at most a certain value
* `:within <min> <max> <keys> [msg]` - must be within a certain range (inclusive)
* `:after <date> <keys> [msg]` - must be after a certain date
* `:before <date> <keys> [msg]` - must be before a certain date
* `:in <coll> <keys> [msg]` - must be contained within a collection
* `:us-zip <keys> [msg]` - must be a valid US zip code
* `:us-state <keys> [msg]` - must be a valid two-letter US state code
* `:ca-state <keys> [msg]` - must be a valid two-letter Canadian province code
* `:country <keys> [msg]` - must be a valid ISO alpha2 country code
* Datatype validations: `:string`, `:boolean`, `:integer`, `:float`, `:decimal`, `:date`
* Datatype collection validations: `:strings`, `:booleans`, `:integers`, `:floats`, `:decimals`, `:dates`

#### `:validator`

A function that takes a map of parsed values and returns a problem map or sequence of problem maps. A problem map has the keys `:keys`, indicating which keys were problems (optional), and `:msg`, a description of what's wrong. If `nil` or an empty sequence is returned, validation succeeds. 

```clj
(defn validate-password [values]
  (when (#{"12345" "password" "hunter2"} (:password values))
    {:keys [:password] :msg "You can't use that password"}))
```

See [Verily](https://github.com/jkk/verily) for more about validation functions.

## Form and Field Specifications

Valid keys for a form specification include the following HTML form attributes:

      :action :method :enctype :accept :name :id :class
      :onsubmit :onreset :accept-charset :autofill

And the following special keys:

      :renderer     - Determines the type of renderer to use. Built-in options:
                        :bootstrap-horizontal (the default)
                        :bootstrap-stacked
                        :table
                        :inline
      :fields       - Sequence of form field specifications. See below.
      :values       - Map of values used to populate the form fields
      :submit-label - Label to use on the submit button. Defaults to "Submit"
      :cancel-href  - When provided, shows a "Cancel" link or button next to the
                      submit button
      :validations  - A sequence of validation specifications
      :validator    - A function to call to validate parsed values for this
                      form. The function should take a map of values and return
                      a sequence of problem maps for each field that failed to
                      validate. The problem map should contain the keys :keys
                      and :msg.
      :problems     - Sequence of field names or problem maps. Form
                      renderers typically add a class and style to highlight
                      problem fields and, if problem maps are provided,
                      show descriptive messages.
                      
New form renderers can be implemented using the `formative.render-form/render-form*` multimethod.

A field specification is a map with keys corresponding to HTML attributes and
the following special keys:

      :name         - Required name of the field, a keyword
      :label        - Optional display name. Auto-generated from :name if not provided
      :type         - UI type of the field. Defaults to :text. Built-in types
                      include: :text, :textarea, :select, :checkbox,
                      :checkboxes, :radios, :html, :heading, :us-state,
                      :ca-state, :country, :date-select, :currency. Each type may
                      have particular keys that it makes use of.

                      Selection fields such as :select, :checkboxes, and :radio
                      expect an :options key, which is a collection of options
                      which conform to one of the following formats:
                        - ["value" "label"]
                        - {:value "value" :label "label"}
                        - "value and label"

                      The :heading type expects a :text key, a string or Hiccup data.

                      The :html type expects an :html key, a string or Hiccup data.
      :datatype     - Optional. Datatype of the field used for parsing. Can be one of:
                      :str, :int, :long, :boolean, :float, :double, :decimal,
                      :bigint, :date, :file, :currency. Defaults to :str.

                      All types can be appended with an "s" when a sequence
                      is expected - e.g., :ints for a sequence of integers. This
                      is useful for fields that have composite values, such as
                      :checkboxes.

                      :date field values are expected to be in yyyy-MM-dd
                      format by default. Set :date-format to change that.

                      :file fields must have an :upload-handler key which is
                      a function that takes two arguments: the field
                      specification, and the Ring file upload payload.

## Field Types

The `:type` of a field can determine how it renders, behaves, gets parsed, and validated.

Without any `:type`, a "text" input type is assumed. If a `:type` is provided that Formative doesn't recognize, an `<input>` element with that type will be assumed.

Built-in types:

* __`:text`__
* __`:textarea`__
* __`:select`__ - special keys:
	* `:options` - options to display; see below for format
	* `:placeholder` - will be used as the text for a first, disabled option
	* `:first-option` - an option to prepend to the other options
* __`:checkbox`__ - defaults to true/false when no :value is given. Special keys:
	* `:value` value of a checked input (default `true`)
	* `:unchecked-value` value to use when the input is unchecked (default `false`)
* __`:checkboxes`__ - multiple checkboxes that parse to a collection of values. Special keys:
	* `:options` - options to display; see below for format
	* `:cols` - number of columns to group checkboxes into
* __`:radios`__ - multiple radio inputs that parse to a single value. Special keys:
	* `:options` - options to display; see below for format
* __`:html`__ - custom, unlabeled HTML. Not included in parsing. Special keys:
	* `:html` - HTML or Hiccup data
* __`:labeled-html`__ - custom, labeled HTML. Not included in parsing. Special keys:
	* `:html` - HTML or Hiccup data
* __`:heading`__ - form heading. Special keys:
	* `:text` - heading text
* __`:email`__ 
* __`:us-state`__ - United States state
* __`:us-zip`__ - United States ZIP code
* __`:ca-state`__ - Canadian province
* __`:country`__ - Country
* __`:date-select`__ - Date selector. Renders as multiple :select fields, parses as a java.util.Date
	* `:year-start`
	* `:year-end`
* __`:year-select`__ - Year selector, parses to integer
	* `:start`
	* `:end`
* __`:month-select`__ - Month selector, parses to integer (1-12)
	* `:numbers` - when true, shows numbers instead of month names
* __`:currency`__ - parses as a :decimal datatype
* __`:file`__ - file upload. Special keys:
	* `:upload-handler` - handler called when a file is uploaded. The field's specification and Ring param value are passed as arguments to the handler. The handler can return whatever value is appropriate (e.g., a String or a File).
* __`:submit`__ - submit button (included by default, but can be added explicitly if you prefer)

The `:options` key for `:select` and other types accepts a collection of any of the following formats:

* ["value" "label"]
* {:value "value" :label "label"}
* "value and label"

Field types are extensible with the `formative.render-field/render-field` and `formative.parse/parse-input` multimethods.

## Realistic Example

```clj
(ns example.core
  (:require [formative.core :as f]
            [formative.parse :as fp]
            [hiccup.page :as page]
            [compojure.core :refer [defroutes GET POST]]))

(def example-form
  {:method :post
   :fields [{:name :h1 :type :heading :text "Section 1"}
            {:name :full-name}
            {:name :email :type :email}
            {:name :spam :type :checkbox :label "Yes, please spam me."}
            {:name :password :type :password}
            {:name :password-confirm :type :password}
            {:name :h2 :type :heading :text "Section 2"}
            {:name :date :type :date-select}
            {:name :flavors :type :checkboxes
             :options ["Chocolate" "Vanilla" "Strawberry" "Mint"]}]
   :validations [[:required [:full-name :email :password]]
                 [:min-length 8 :password]
                 [:equal [:password :password-confirm]]
                 [:min-length 2 :flavors "Please select two or more flavors"]]})

(defn layout [& body]
  (page/html5
    [:head
     [:title "Example"]
     (page/include-css "//cdnjs.cloudflare.com/ajax/libs/twitter-bootstrap/2.2.2/css/bootstrap.min.css")
     [:style "body { margin: 2em; }"]]
    [:body
     body]))

(defn show-example-form [params & {:keys [problems]}]
  (let [defaults {:spam true}]
    (layout
      [:h1 "Example"]
      (f/render-form (assoc example-form
                            :values (merge defaults params)
                            :problems problems)))))

(defn submit-example-form [params]
  (fp/with-fallback (partial show-example-form params :problems)
    (let [values (fp/parse-params example-form params)]
      (layout
        [:h1 "Thank you!"]
        [:pre (prn-str values)]))))

(defroutes example-routes
  (GET "/example" [& params] (show-example-form params))
  (POST "/example" [& params] (submit-example-form params)))

```

The form will look something like this:

![form](https://github.com/jkk/formative/raw/master/doc/fuller-example.png)

## License

Copyright © 2012 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.

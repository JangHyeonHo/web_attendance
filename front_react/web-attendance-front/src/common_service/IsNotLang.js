import React from "react";

export default function IsNotLang(lang, defaultLang){
    return lang ? lang : defaultLang;
}
package com.dmh.accounts.model;

/** Marca de la tarjeta, deducida de los primeros digitos del numero. */
public enum CardBrand {
    VISA,
    MASTERCARD,
    AMEX,
    OTHER;

    public static CardBrand fromNumber(String number) {
        if (number.startsWith("4")) {
            return VISA;
        }
        if (number.startsWith("34") || number.startsWith("37")) {
            return AMEX;
        }
        int firstTwo = Integer.parseInt(number.substring(0, 2));
        int firstFour = Integer.parseInt(number.substring(0, 4));
        if ((firstTwo >= 51 && firstTwo <= 55) || (firstFour >= 2221 && firstFour <= 2720)) {
            return MASTERCARD;
        }
        return OTHER;
    }
}

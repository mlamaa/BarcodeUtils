package com.hibcc;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by mlamaa on Jul 06 2020.
 */
public class HIBC {

    private String barcode;

    public HIBC() {
    }

    public Decoded decode(String barcode){
        Decoded decoded = new Decoded();
        this.barcode = barcode;

        decoded.barcode = barcode;

        if(barcode == null || barcode.isEmpty()){
            decoded.error = Error.EMPTY_BARCODE;
            return decoded;
        }

        // remove leading * and check
        if(barcode.startsWith("*")){
            barcode = barcode.substring(1);
            if(barcode.isEmpty()){
                decoded.error = Error.EMPTY_BARCODE;
                return decoded;
            }
        }

        // remove trailing * and check
        if(barcode.endsWith("*")){
            barcode = barcode.substring(0, barcode.length() - 1);
            if(barcode.isEmpty()){
                decoded.error = Error.EMPTY_BARCODE;
                return decoded;
            }
        }

        // check for + character
        if(barcode.charAt(0) != '+'){
            decoded.error = Error.BARCODE_NOT_HIBC;
            return decoded;
        } else {
            barcode = barcode.substring(1);
        }

        // check minimum barcode length
        if(barcode.length() < 4){
            decoded.error = Error.INVALID_BARCODE;
            return decoded;
        }

        String potentialCheckAndLinkCharacters = barcode.substring(barcode.length() - 2);
        barcode = barcode.substring(0, barcode.length() - 2);

        String[] lines = barcode.split("[\\/]");

        if(lines.length == 1){
            if(Character.isLetter(lines[0].charAt(0))){
                decoded = processLine1(decoded, Type.LINE_1, lines[0] + potentialCheckAndLinkCharacters);
            } else {
                decoded = processLine2(decoded, Type.LINE_2, lines[0] + potentialCheckAndLinkCharacters);
            }
            return decoded;

        }else if(lines.length == 2){
            decoded = processLine1(decoded, Type.CONCATENATED, lines[0]);
            decoded = assign(decoded, processLine2(new Decoded(), Type.CONCATENATED, lines[1] + potentialCheckAndLinkCharacters));
        } else {
            decoded.error = Error.INVALID_BARCODE;
            return decoded;
        }

        return decoded;

    }


    private Decoded processLine1(Decoded decoded, Type line1, String barcode) {
        decoded.type = line1;
        if(barcode.length() < 4){
            decoded.error = Error.INVALID_LINE_1;
            return decoded;
        }

        decoded.labelerId = barcode.substring(0, 4);
        barcode = barcode.substring(4);

        if(barcode.isEmpty()){
            decoded.error = Error.INVALID_LINE_1;
            return decoded;
        }

        if (decoded.type != Type.CONCATENATED) {
            decoded.check = barcode.charAt(barcode.length() - 1);
            barcode = barcode.substring(0, barcode.length() - 1);
            if (barcode.isEmpty()) {
                decoded.error = Error.INVALID_LINE_1;
                return decoded;
            }
        }

        decoded.uom = barcode.charAt(barcode.length() - 1) - '0';
        barcode = barcode.substring(0, barcode.length() - 1);
        if (barcode.isEmpty()) {
            decoded.error = Error.INVALID_LINE_1;
            return decoded;
        }
        decoded.product = barcode;

        return decoded;
    }

    private Decoded processLine2(Decoded decoded, Type line2, String barcode) {
        decoded.type = line2;

        if(barcode.length() > 0 && Character.isDigit(barcode.charAt(0))){
            if(barcode.length() < 5){
                decoded.error = Error.INVALID_DATE;
                return decoded;
            }
            String dateFormat = "yyddd";
            DateFormat dtf = new SimpleDateFormat(dateFormat) {}; // odd trick so that you can use date time
            Date date = null;
            try {
                date = dtf.parse(barcode.substring(0, 5));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            decoded.date = dtf.format(date);
            decoded = assign(decoded, decodeLotSerialCheckLink(barcode.substring(5), line2, "lot", false));
        } else if(barcode.length() > 2 && barcode.charAt(0) == '$' && Character.isDigit(barcode.charAt(1))){
            decoded = assign(decoded, decodeLotSerialCheckLink(barcode.substring(1), line2, "lot", false));
        } else if(barcode.length() > 3 && barcode.substring(0, 2).equals("$+") && Character.isDigit(barcode.charAt(2))){
            decoded = assign(decoded, decodeLotSerialCheckLink(barcode.substring(2), line2, "serial", false));
        } else if(barcode.length() > 3 && barcode.substring(0, 2).equals("$$") && Character.isDigit(barcode.charAt(2))){
            decoded = assign(decoded, decodeLotSerialCheckLink(barcode.substring(2), line2, "lot", true));
            /*
             */
            if (decoded.error == null) {
                extractMomentFromString(decoded, "lot", "date");
            }
        } else if(barcode.length() > 3 && barcode.substring(0, 3).equals("$$+")){
            decoded = assign(decoded, decodeLotSerialCheckLink(barcode.substring(3), line2, "serial", true));
            extractMomentFromString(decoded, "serial", "date");
        } else {
            decoded.error = Error.INVALID_BARCODE;
        }

        return decoded;
    }

    private void extractMomentFromString(Decoded decoded, String property, String dateString) {
        // could check property type with the type sent in, currently assumes the same because they are
        String tempString = decoded.propertyValue;

        if(tempString == null || tempString.isEmpty()){
            return;
        }

        int hibcDateFormatKey;
        try{
            hibcDateFormatKey = Integer.parseInt(tempString.substring(0, 1));
        } catch (NumberFormatException ex){
            decoded.error = Error.INVALID_DATE;
            return;
        }

        String dateFormat;

        switch(hibcDateFormatKey){
            case 0:
            case 1:
                dateFormat = "MMyy";
                break;
            case 2:
                dateFormat = "MMddyy";
                break;
            case 3:
                dateFormat = "yyMMdd";
                break;
            case 4:
                dateFormat = "yyMMddHH";
                break;
            case 5:
                dateFormat = "yyddd";
                break;
            case 6:
                dateFormat = "yydddHH";
                break;
            case 7:
                // no date here so can assign normal string
                decoded.propertyValue = tempString.substring(1);
                return;
            default:
                // no date
                return;
        }

        if(hibcDateFormatKey > 1){
            tempString = tempString.substring(1);
        }

        if(tempString.length() < dateFormat.length()){
            decoded.error = Error.INVALID_DATE;
            return;
        }

        // convert date to string for decoded date property
        DateFormat dtf = new SimpleDateFormat(dateFormat) {}; // odd trick so that you can use date time
        Date date = null;
        try {
            date = dtf.parse(tempString.substring(0, dateFormat.length()));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        decoded.date = dtf.format(date);

        decoded.propertyValue = tempString.substring(dateFormat.length());

    }

    private Decoded decodeLotSerialCheckLink(String string, Type barcodeType, String propertyName, boolean hasQuantity){
        // clear decoded and create new stuff from variables
        Decoded decoded = new Decoded();

        if(string.isEmpty()){
            decoded.error = Error.EMPTY_CHECK_CHARACTER;
            return decoded;
        }

        if(hasQuantity){
            string = extractQuantityFromString(decoded, string);
        }

        // check character
        decoded.check = string.charAt(string.length() - 1);
        string = string.substring(0, string.length() - 1);

        // LotOrSerial and LinkCharacter
        if(barcodeType == Type.LINE_2){
            if(string.isEmpty()){
                decoded.error = Error.EMPTY_LINK_CHARACTER;
                return decoded;
            }
            decoded.link = string.charAt(string.length() - 1);
            decoded.property = PropertyType.fromString(propertyName);
            decoded.propertyValue = string.substring(0, string.length() - 1);
        } else {
            decoded.property = PropertyType.fromString(propertyName);
            decoded.propertyValue = string;
        }

        return decoded;
    }


    private String extractQuantityFromString(Decoded decoded, String string) {
        Character digit = string.charAt(0);
        if(!Character.isDigit(digit)){
            return string;
        }

        Integer i = Integer.parseInt(String.valueOf(digit));
        int length;

        switch(i){
            case 8:
                length = 2;
                break;
            case 9:
                length = 5;
                break;
            default:
                // no quantity
                return string;
        }

        string = string.substring(1);
        // check if valid number, update decoded.quantity if so, otherwise update error
        try {
            int quantity = Integer.parseInt(string.substring(0, length));
            string = string.substring(length);
            decoded.quantity = quantity;
        } catch (NumberFormatException ex){
            decoded.error = Error.INVALID_QUANTITY;
            string = string.substring(length);
        }

        return string;
    }

    private Decoded assign(Decoded target, Decoded source){  // in theory could be varargs in the future, but not currently
        // just assigns values in the second object to the first, if they exist
        if(source.type != null){
            target.type = source.type;
        }
        if(source.error != null){
            target.error = source.error;
        }
        if(source.barcode != null){
            target.barcode = source.barcode;
        }
        if(source.labelerId != null){
            target.labelerId = source.labelerId;
        }
        if(source.check != null){
            target.check = source.check;
        }
        if(source.link != null){
            target.link = source.link;
        }
        if(source.property != null){
            target.property = source.property;
            if(source.propertyValue != null){
                target.propertyValue = source.propertyValue;
            }
        }
        if(source.date != null){
            target.date = source.date;
        }
        if(source.uom != null){
            target.uom = source.uom;
        }
        if(source.quantity != null){
            target.quantity = source.quantity;
        }
        if(source.product != null){
            target.product = source.product;
        }

        return target;
    }


    public class Decoded{
        Type type;
        Error error;
        String barcode;
        String labelerId;
        Character check;
        Character link;
        PropertyType property;
        String propertyValue;
        Integer quantity;
        Integer uom;
        String date;
        String product;

        public Type getType() {
            return type;
        }

        public Error getError() {
            return error;
        }

        public String getBarcode() {
            return barcode;
        }

        public String getLabelerId() {
            return labelerId;
        }

        public Character getCheck() {
            return check;
        }

        public Character getLink() {
            return link;
        }

        public PropertyType getProperty() {
            return property;
        }

        public String getPropertyValue() {
            return propertyValue;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public Integer getUom() {
            return uom;
        }

        public String getDate() {
            return date;
        }

        public String getProduct() {
            return product;
        }
    }

    public enum Type{
        CONCATENATED(1),
        LINE_1(2),
        LINE_2(3);

        public int typeCode;

        Type(int typeCode) {
            this.typeCode = typeCode;
        }

    }

    public enum PropertyType {
        LOT("lot"),
        SERIAL("serial");

        public String text;

        PropertyType(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public static PropertyType fromString(String text){
            if(text != null){
                for(PropertyType prop : PropertyType.values()){
                    if(text.equalsIgnoreCase(prop.text)){
                        return prop;
                    }
                }
            }
            return null;
        }
    }

    public enum Error {
        EMPTY_BARCODE,
        BARCODE_NOT_HIBC,
        INVALID_BARCODE,
        INVALID_DATE,
        EMPTY_CHECK_CHARACTER,
        EMPTY_LINK_CHARACTER,
        INVALID_QUANTITY,
        INVALID_LINE_1
    }

}
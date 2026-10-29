/*
 * Copyright (C)      2023 - Peter Korf <peter@niendo.de>
 * and Contributors.
 *
 * This file is part of ImapNotes3.
 *
 * ImapNotes3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.niendo.ImapNotes3.Miscs;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmtpServerNameFinder {
    private static final String STANDARD_PREFIX = "imap.";
    private static final Map<String, String> domainMappings = new HashMap<>();

    static {
        // Add domain mappings here // only non standards (smtp = standard)
        //domainMappings.put("gmail.com", "smtp.gmail.com");
        //domainMappings.put("yahoo.com", "smtp.mail.yahoo.com");
        //domainMappings.put("hotmail.com", "smtp.live.com");
        domainMappings.put("outlook.com", "smtp-mail.outlook.com");
        //domainMappings.put("aol.com", "smtp.aol.com");
        //domainMappings.put("mail.com", "smtp.mail.com");
        domainMappings.put("gmx.com", "mail.gmx.com");
        //domainMappings.put("icloud.com", "smtp.mail.me.com");
        //domainMappings.put("yandex.com", "smtp.yandex.com");
        //domainMappings.put("zoho.com", "smtp.zoho.com");
        domainMappings.put("posteo.at", "posteo.at");
        domainMappings.put("posteo.be", "posteo.be");
        domainMappings.put("posteo.ca", "posteo.ca");
        domainMappings.put("posteo.ch", "posteo.ch");
        domainMappings.put("posteo.cl", "posteo.cl");
        domainMappings.put("posteo.co", "posteo.co");
        domainMappings.put("posteo.co.uk", "posteo.co.uk");
        domainMappings.put("posteo.com.br", "posteo.com.br");
        domainMappings.put("posteo.cr", "posteo.cr");
        domainMappings.put("posteo.cz", "posteo.cz");
        domainMappings.put("posteo.de", "posteo.de");
        domainMappings.put("posteo.dk", "posteo.dk");
        domainMappings.put("posteo.ee", "posteo.ee");
        domainMappings.put("posteo.es", "posteo.es");
        domainMappings.put("posteo.eu", "posteo.eu");
        domainMappings.put("posteo.fi", "posteo.fi");
        domainMappings.put("posteo.gl", "posteo.gl");
        domainMappings.put("posteo.gr", "posteo.gr");
        domainMappings.put("posteo.hn", "posteo.hn");
        domainMappings.put("posteo.hr", "posteo.hr");
        domainMappings.put("posteo.hu", "posteo.hu");
        domainMappings.put("posteo.ie", "posteo.ie");
        domainMappings.put("posteo.in", "posteo.in");
        domainMappings.put("posteo.is", "posteo.is");
        domainMappings.put("posteo.it", "posteo.it");
        domainMappings.put("posteo.jp", "posteo.jp");
        domainMappings.put("posteo.la", "posteo.la");
        domainMappings.put("posteo.li", "posteo.li");
        domainMappings.put("posteo.lt", "posteo.lt");
        domainMappings.put("posteo.lu", "posteo.lu");
        domainMappings.put("posteo.me", "posteo.me");
        domainMappings.put("posteo.mx", "posteo.mx");
        domainMappings.put("posteo.my", "posteo.my");
        domainMappings.put("posteo.net", "posteo.net");
        domainMappings.put("posteo.nl", "posteo.nl");
        domainMappings.put("posteo.no", "posteo.no");
        domainMappings.put("posteo.nz", "posteo.nz");
        domainMappings.put("posteo.org", "posteo.org");
        domainMappings.put("posteo.pe", "posteo.pe");
        domainMappings.put("posteo.pl", "posteo.pl");
        domainMappings.put("posteo.pm", "posteo.pm");
        domainMappings.put("posteo.pt", "posteo.pt");
        domainMappings.put("posteo.ro", "posteo.ro");
        domainMappings.put("posteo.se", "posteo.se");
        domainMappings.put("posteo.sg", "posteo.sg");
        domainMappings.put("posteo.si", "posteo.si");
        domainMappings.put("posteo.tn", "posteo.tn");
        domainMappings.put("posteo.uk", "posteo.uk");
        domainMappings.put("posteo.us", "posteo.us");
        //domainMappings.put("protonmail.com", "smtp.protonmail.com");
        //domainMappings.put("fastmail.com", "smtp.fastmail.com");
        //domainMappings.put("qq.com", "smtp.qq.com");
        //domainMappings.put("163.com", "smtp.163.com");
        //domainMappings.put("sina.com", "smtp.sina.com");
        //domainMappings.put("aliyun.com", "smtp.aliyun.com");
        //domainMappings.put("yeah.net", "smtp.yeah.net");
        //domainMappings.put("foxmail.com", "smtp.foxmail.com");
        domainMappings.put("t-online.de", "securesmtp.t-online.de");
        domainMappings.put("verizon.net", "outgoing.verizon.net");
        // Add more domain mappings as needed
    }

    public static String getSmtpServerName(String emailAddress) {
        // Extract domain name from email address
        String domainName = extractDomainName(emailAddress);

        // Map domain name to SMTP server name
        String smtpServerName = domainMappings.get(domainName);

        if (smtpServerName != null) {
            return smtpServerName;
        } else {
            return STANDARD_PREFIX + domainName;
        }
    }

    private static String extractDomainName(String emailAddress) {
        String domainName = "";
        Pattern pattern = Pattern.compile("@(.+)$");
        Matcher matcher = pattern.matcher(emailAddress);
        if (matcher.find()) {
            domainName = matcher.group(1);
        }
        return domainName;
    }

}

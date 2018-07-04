/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.ibm.websphere.samples.daytrader;

import javax.persistence.*;

import com.ibm.websphere.samples.daytrader.util.Log;


import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;


@Entity(name = "accountprofileejb")
@Table(name = "accountprofileejb")
@NamedQueries( {
        @NamedQuery(name = "accountprofileejb.findByAddress", query = "SELECT a FROM accountprofileejb a WHERE a.address = :address"),
        @NamedQuery(name = "accountprofileejb.findByPasswd", query = "SELECT a FROM accountprofileejb a WHERE a.passwd = :passwd"),
        @NamedQuery(name = "accountprofileejb.findByUserid", query = "SELECT a FROM accountprofileejb a WHERE a.userID = :userid"),
        @NamedQuery(name = "accountprofileejb.findByEmail", query = "SELECT a FROM accountprofileejb a WHERE a.email = :email"),
        @NamedQuery(name = "accountprofileejb.findByCreditcard", query = "SELECT a FROM accountprofileejb a WHERE a.creditCard = :creditcard"),
        @NamedQuery(name = "accountprofileejb.findByFullname", query = "SELECT a FROM accountprofileejb a WHERE a.fullName = :fullname")
    })
@XmlRootElement(name="AccountProfileDataBean")
public class AccountProfileDataBean implements java.io.Serializable {

    /* Accessor methods for persistent fields */

    private static final long serialVersionUID = 2794584136675420624L;

	@Id
    @Column(name = "USERID", nullable = false)
    private String userID;              /* userID */
    
    @Column(name = "PASSWD")
    // DHV FOR STEP 2
    //private String passwd;              /* password */
    private String password;              /* password */
    
    @Column(name = "FULLNAME")
    private String fullName;            /* fullName */
    
    @Column(name = "ADDRESS")
    private String address;             /* address */
    
    @Column(name = "EMAIL")
    private String email;               /* email */
    
    @Column(name = "CREDITCARD")
    private String creditCard;          /* creditCard */
    
    @OneToOne(mappedBy="profile", fetch=FetchType.LAZY)
    private AccountDataBean account;

//    @Version
//    private Integer optLock;

    public AccountProfileDataBean() {
    }
    
    public AccountProfileDataBean(String userID,
            String password,
            String fullName,
            String address,
            String email,
            String creditCard) {
        setUserID(userID);
        setPassword(password);
        setFullName(fullName);
        setAddress(address);
        setEmail(email);
        setCreditCard(creditCard);
    }

//    public static AccountProfileDataBean getRandomInstance() {
//        return new AccountProfileDataBean(
//                TradeConfig.rndUserID(),                        // userID
//                TradeConfig.rndUserID(),                        // passwd
//                TradeConfig.rndFullName(),                      // fullname
//                TradeConfig.rndAddress(),                       // address
//                TradeConfig.rndEmail(TradeConfig.rndUserID()),  //email
//                TradeConfig.rndCreditCard()                     // creditCard
//        );
//    }

    public String toString() {
        return "\n\tAccount Profile Data for userID:" + getUserID()
                + "\n\t\t   passwd:" + getPassword()
                + "\n\t\t   fullName:" + getFullName()
                + "\n\t\t    address:" + getAddress()
                + "\n\t\t      email:" + getEmail()
                + "\n\t\t creditCard:" + getCreditCard()
                ;
    }

    public String toHTML() {
        return "<BR>Account Profile Data for userID: <B>" + getUserID() + "</B>"
                + "<LI>   passwd:" + getPassword() + "</LI>"
                + "<LI>   fullName:" + getFullName() + "</LI>"
                + "<LI>    address:" + getAddress() + "</LI>"
                + "<LI>      email:" + getEmail() + "</LI>"
                + "<LI> creditCard:" + getCreditCard() + "</LI>"
                ;
    }

    public void print() {
System.out.println(this.toString());
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getPassword() {
        // DHV FOR STEP 2
    	//return passwd;
    	return password;
    }

    public void setPassword(String password) {
        // DHV FOR STEP 2
    	//this.passwd = password;
    	this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCreditCard() {
        return creditCard;
    }

    public void setCreditCard(String creditCard) {
        this.creditCard = creditCard;
    }

    // DHV FOR STEP 2 - Can't parse json back to account ... infinite recursion
    @XmlTransient
    public AccountDataBean getAccount() {
        return account;
    }

    public void setAccount(AccountDataBean account) {
        this.account = account;
    }
    
    @Override
    public int hashCode() {
        int hash = 0;
        hash += (this.userID != null ? this.userID.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof AccountProfileDataBean)) {
            return false;
        }
        AccountProfileDataBean other = (AccountProfileDataBean)object;
        if (this.userID != other.userID && (this.userID == null || !this.userID.equals(other.userID))) return false;
        return true;
    }
}

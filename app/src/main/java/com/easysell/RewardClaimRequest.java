package com.easysell;

import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

public class RewardClaimRequest {
    private String docId;

    @PropertyName("buyerUid")
    private String buyerUid;

    @PropertyName("buyerName")
    private String buyerName;

    @PropertyName("buyerEmail")
    private String buyerEmail;

    @PropertyName("rewardId")
    private String rewardId;

    @PropertyName("rewardTitle")
    private String rewardTitle;

    @PropertyName("rewardType")
    private String rewardType;

    @PropertyName("pointsCost")
    private Long pointsCost;

    private String status;
    private String storeHandle;

    @ServerTimestamp
    private Date createdAt;

    @ServerTimestamp
    private Date approvedAt;

    @ServerTimestamp
    private Date fulfilledAt;

    public RewardClaimRequest() {
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    @PropertyName("buyerUid")
    public String getBuyerUid() {
        return buyerUid;
    }

    @PropertyName("buyerUid")
    public void setBuyerUid(String buyerUid) {
        this.buyerUid = buyerUid;
    }

    @PropertyName("buyerName")
    public String getBuyerName() {
        return buyerName;
    }

    @PropertyName("buyerName")
    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    @PropertyName("buyerEmail")
    public String getBuyerEmail() {
        return buyerEmail;
    }

    @PropertyName("buyerEmail")
    public void setBuyerEmail(String buyerEmail) {
        this.buyerEmail = buyerEmail;
    }

    @PropertyName("rewardId")
    public String getRewardId() {
        return rewardId;
    }

    @PropertyName("rewardId")
    public void setRewardId(String rewardId) {
        this.rewardId = rewardId;
    }

    @PropertyName("rewardTitle")
    public String getRewardTitle() {
        return rewardTitle;
    }

    @PropertyName("rewardTitle")
    public void setRewardTitle(String rewardTitle) {
        this.rewardTitle = rewardTitle;
    }

    @PropertyName("rewardType")
    public String getRewardType() {
        return rewardType;
    }

    @PropertyName("rewardType")
    public void setRewardType(String rewardType) {
        this.rewardType = rewardType;
    }

    @PropertyName("pointsCost")
    public Long getPointsCost() {
        return pointsCost;
    }

    @PropertyName("pointsCost")
    public void setPointsCost(Long pointsCost) {
        this.pointsCost = pointsCost;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStoreHandle() {
        return storeHandle;
    }

    public void setStoreHandle(String storeHandle) {
        this.storeHandle = storeHandle;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Date approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Date getFulfilledAt() {
        return fulfilledAt;
    }

    public void setFulfilledAt(Date fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }
}

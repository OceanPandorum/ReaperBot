package reaper.presence.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import reaper.presence.AdminActionType;

import java.time.*;
import java.util.*;

@Table("admin_action")
public class AdminAction{
    @Id
    @JsonProperty
    private String id;

    @Column("admin_id")
    @JsonProperty("admin_id")
    private String adminId;

    @Column("admin_nickname")
    @JsonProperty("admin_nickname")
    private String adminNickname;

    @Column("target_id")
    @JsonProperty("target_id")
    private String targetId;

    @Column("target_nickname")
    @JsonProperty("target_nickname")
    private String targetNickname;

    @JsonProperty
    private AdminActionType type;

    @JsonProperty
    private String reason;

    @JsonProperty
    private Instant timestamp;

    @Column("end_timestamp")
    @JsonProperty("end_timestamp")
    private Instant endTimestamp;

    public String id(){
        return id;
    }

    public void id(String id){
        this.id = id;
    }

    public String adminId(){
        return adminId;
    }

    public void adminId(String adminId){
        this.adminId = adminId;
    }

    public String adminNickname(){
        return adminNickname;
    }

    public void adminNickname(String adminNickname){
        this.adminNickname = adminNickname;
    }

    public String targetId(){
        return targetId;
    }

    public void targetId(String targetId){
        this.targetId = targetId;
    }

    public String targetNickname(){
        return targetNickname;
    }

    public void targetNickname(String targetNickname){
        this.targetNickname = targetNickname;
    }

    public AdminActionType type(){
        return type;
    }

    public void type(AdminActionType type){
        this.type = type;
    }

    public Optional<String> reason(){
        return Optional.ofNullable(reason);
    }

    public void reason(String reason){
        this.reason = reason;
    }

    public Instant timestamp(){
        return timestamp;
    }

    public void timestamp(Instant timestamp){
        this.timestamp = timestamp;
    }

    public Instant endTimestamp(){
        return endTimestamp;
    }

    public void endTimestamp(Instant endTimestamp){
        this.endTimestamp = endTimestamp;
    }

    public AdminAction plusEndTimestamp(Instant endTimestamp){
        this.endTimestamp = endTimestamp.plusSeconds(endTimestamp.getEpochSecond() - this.endTimestamp.getEpochSecond());
        return this;
    }

    @Override
    public boolean equals(Object o){
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        AdminAction that = (AdminAction)o;
        return Objects.equals(id, that.id) &&
               Objects.equals(adminId, that.adminId) &&
               Objects.equals(adminNickname, that.adminNickname) &&
               Objects.equals(targetId, that.targetId) &&
               Objects.equals(targetNickname, that.targetNickname) &&
               type == that.type &&
               Objects.equals(reason, that.reason) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(endTimestamp, that.endTimestamp);
    }

    @Override
    public int hashCode(){
        return Objects.hash(id, adminId, adminNickname, targetId, targetNickname, type, reason, timestamp, endTimestamp);
    }

    @Override
    public String toString(){
        return "AdminAction{" +
               "id='" + id + '\'' +
               ", adminId='" + adminId + '\'' +
               ", adminNickname='" + adminNickname + '\'' +
               ", targetId='" + targetId + '\'' +
               ", targetNickname='" + targetNickname + '\'' +
               ", type=" + type +
               ", reason='" + reason + '\'' +
               ", timestamp=" + timestamp +
               ", endTimestamp=" + endTimestamp +
               '}';
    }
}

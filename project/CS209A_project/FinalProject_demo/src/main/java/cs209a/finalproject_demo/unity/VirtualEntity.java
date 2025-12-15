package cs209a.finalproject_demo.unity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class VirtualEntity {

    @Id
    private Long user_id;

    public VirtualEntity() {}

    public VirtualEntity(Long user_id) {
        this.user_id = user_id;
    }

    public Long getUser_id() {
        return user_id;
    }

    public void setUser_id(Long user_id) {
        this.user_id = user_id;
    }
}
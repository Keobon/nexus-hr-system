package com.nexuslabs.hr.domain.org.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 지금 조직 트리(사용 중지된 조직 포함)를 읽어 {@link TopOrgGroups} 를 만든다. */
@Component
public class TopOrgGroupReader {

    private final JdbcTemplate jdbc;

    public TopOrgGroupReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TopOrgGroups read(long companyId) {
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        Map<Long, Integer> order = new HashMap<>();
        long[] root = {-1};
        jdbc.query("SELECT id, parent_id, name FROM org_unit WHERE company_id = ? ORDER BY sort_order, id",
                rs -> {
                    long id = rs.getLong("id");
                    Long p = rs.getObject("parent_id", Long.class);
                    names.put(id, rs.getString("name"));
                    order.put(id, order.size());
                    if (p == null) {
                        root[0] = id;
                    } else {
                        parent.put(id, p);
                    }
                },
                companyId);
        Map<Long, Long> group = new HashMap<>();
        for (long id : names.keySet()) {
            long g = id;
            while (parent.containsKey(g) && parent.get(g) != root[0]) {
                g = parent.get(g);
            }
            group.put(id, g);
        }
        return new TopOrgGroups(root[0], group, names, order);
    }
}

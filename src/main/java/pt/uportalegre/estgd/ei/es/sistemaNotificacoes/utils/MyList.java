package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.utils;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MyList<T> implements List<T> {

    private final List<T> list = new ArrayList<>();
    private final Supplier<String> supplier;
    private final Consumer<String> consumer;
    private final ObjectMapper objectMapper;
    private final Class<T> elementType;

    public MyList(Supplier<String> supplier, Consumer<String> consumer, Class<T> elementType) {
        this.supplier = supplier;
        this.consumer = consumer;
        this.objectMapper = new ObjectMapper();
        this.elementType = elementType;
        carregarDoJson();
    }

    private void carregarDoJson() {
        if (supplier == null) {
            return;
        }

        String json = supplier.get();
        if (json == null || json.isBlank()) {
            return;
        }

        try {
            list.addAll(objectMapper.readerForListOf(elementType).readValue(json));
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing JSON", e);
        }
    }

    private void synchronize() {
        if (consumer == null) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(list);
            consumer.accept(json);
        } catch (Exception e) {
            throw new RuntimeException("Error synchronizing list", e);
        }
    }

    @Override
    public boolean add(T element) {
        list.add(element);
        synchronize();
        return true;
    }

    @Override
    public void add(int index, T element) {
        list.add(index, element);
        synchronize();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean changed = list.addAll(c);
        if (changed) {
            synchronize();
        }
        return changed;
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        boolean changed = list.addAll(index, c);
        if (changed) {
            synchronize();
        }
        return changed;
    }

    @Override
    public boolean remove(Object o) {
        boolean changed = list.remove(o);
        if (changed) {
            synchronize();
        }
        return changed;
    }

    @Override
    public T remove(int index) {
        T removed = list.remove(index);
        synchronize();
        return removed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = list.removeAll(c);
        if (changed) {
            synchronize();
        }
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = list.retainAll(c);
        if (changed) {
            synchronize();
        }
        return changed;
    }

    @Override
    public void clear() {
        list.clear();
        synchronize();
    }

    @Override
    public T set(int index, T element) {
        T previous = list.set(index, element);
        synchronize();
        return previous;
    }

    @Override public int size() { return list.size(); }
    @Override public boolean isEmpty() { return list.isEmpty(); }
    @Override public boolean contains(Object o) { return list.contains(o); }
    @Override public Iterator<T> iterator() { return list.iterator(); }
    @Override public Object[] toArray() { return list.toArray(); }
    @Override public <T1> T1[] toArray(T1[] a) { return list.toArray(a); }
    @Override public boolean containsAll(Collection<?> c) { return list.containsAll(c); }
    @Override public T get(int index) { return list.get(index); }
    @Override public int indexOf(Object o) { return list.indexOf(o); }
    @Override public int lastIndexOf(Object o) { return list.lastIndexOf(o); }
    @Override public ListIterator<T> listIterator() { return list.listIterator(); }
    @Override public ListIterator<T> listIterator(int index) { return list.listIterator(index); }
    @Override public List<T> subList(int fromIndex, int toIndex) { return list.subList(fromIndex, toIndex); }
    @Override public boolean equals(Object o) { return list.equals(o); }
    @Override public int hashCode() { return list.hashCode(); }
    @Override public String toString() { return list.toString(); }
}

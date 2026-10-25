package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.utils;

import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class myList<T> implements List<T> {

    private final List<T> list = new ArrayList<>();
    private final Supplier<String> supplier;
    private final Consumer<String> consumer;
    private final ObjectMapper objectMapper;
    private final Class<T> elementType;

    public myList(Supplier<String> supplier, Consumer<String> consumer, Class<T> elementType) {
        this(supplier, consumer, new ObjectMapper(), elementType);
    }

    public myList(Supplier<String> supplier, Consumer<String> consumer, ObjectMapper objectMapper, Class<T> elementType) {
        this.supplier = supplier;
        this.consumer = consumer;
        this.objectMapper = objectMapper;
        this.elementType = elementType;
        carregarDoJson();
    }

    private void carregarDoJson() {
        String json = supplier.get();
        if (json == null || json.isBlank()) {
            return;
        }

        try {
            list.addAll(objectMapper.readerForListOf(elementType).readValue(json));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao desserializar JSON", e);
        }
    }

    private void sincronizar() {
        try {
            String json = objectMapper.writeValueAsString(list);
            consumer.accept(json);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao sincronizar lista", e);
        }
    }

    @Override
    public boolean add(T element) {
        boolean result = list.add(element);
        if (result) {
            sincronizar();
        }
        return result;
    }

    @Override
    public void add(int index, T element) {
        list.add(index, element);
        sincronizar();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean result = list.addAll(c);
        if (result) {
            sincronizar();
        }
        return result;
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        boolean result = list.addAll(index, c);
        if (result) {
            sincronizar();
        }
        return result;
    }

    @Override
    public boolean remove(Object o) {
        boolean res = list.remove(o);
        if (res) {
            sincronizar();
        }
        return res;
    }

    @Override
    public T remove(int index) {
        T removed = list.remove(index);
        sincronizar();
        return removed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean res = list.removeAll(c);
        if (res) {
            sincronizar();
        }
        return res;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean res = list.retainAll(c);
        if (res) {
            sincronizar();
        }
        return res;
    }

    @Override
    public void clear() {
        if (!list.isEmpty()) {
            list.clear();
            sincronizar();
        }
    }

    @Override
    public T set(int index, T element) {
        T previous = list.set(index, element);
        sincronizar();
        return previous;
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        boolean res = list.removeIf(filter);
        if (res) {
            sincronizar();
        }
        return res;
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        list.replaceAll(operator);
        sincronizar();
    }

    @Override
    public void sort(Comparator<? super T> c) {
        list.sort(c);
        sincronizar();
    }

    @Override
    public Iterator<T> iterator() {
        Iterator<T> it = list.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public T next() {
                return it.next();
            }

            @Override
            public void remove() {
                it.remove();
                sincronizar();
            }
        };
    }

    @Override
    public ListIterator<T> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        ListIterator<T> it = list.listIterator(index);
        return new ListIterator<>() {
            @Override public boolean hasNext() { return it.hasNext(); }
            @Override public T next() { return it.next(); }
            @Override public boolean hasPrevious() { return it.hasPrevious(); }
            @Override public T previous() { return it.previous(); }
            @Override public int nextIndex() { return it.nextIndex(); }
            @Override public int previousIndex() { return it.previousIndex(); }

            @Override
            public void remove() {
                it.remove();
                sincronizar();
            }

            @Override
            public void set(T t) {
                it.set(t);
                sincronizar();
            }

            @Override
            public void add(T t) {
                it.add(t);
                sincronizar();
            }
        };
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return list.subList(fromIndex, toIndex);
    }

    @Override public int size() { return list.size(); }
    @Override public boolean isEmpty() { return list.isEmpty(); }
    @Override public boolean contains(Object o) { return list.contains(o); }
    @Override public boolean containsAll(Collection<?> c) { return list.containsAll(c); }
    @Override public T get(int index) { return list.get(index); }
    @Override public int indexOf(Object o) { return list.indexOf(o); }
    @Override public int lastIndexOf(Object o) { return list.lastIndexOf(o); }
    @Override public Object[] toArray() { return list.toArray(); }
    @Override public <T1> T1[] toArray(T1[] a) { return list.toArray(a); }
    @Override public boolean equals(Object o) { return list.equals(o); }
    @Override public int hashCode() { return list.hashCode(); }
    @Override public String toString() { return list.toString(); }
}

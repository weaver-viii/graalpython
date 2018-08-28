/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.list;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.ListBuiltinsFactory.ListAppendNodeFactory;
import com.oracle.graal.python.builtins.objects.list.ListBuiltinsFactory.ListReverseNodeFactory;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.ListNodes.IndexNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PList)
public class ListBuiltins extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ListBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        public Object repr(Object self,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode repr,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            StringBuilder result = new StringBuilder("[");
            Object iterator = getIterator.executeWith(self);
            boolean initial = true;
            while (true) {
                Object value;
                try {
                    value = next.execute(iterator);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    return result.append(']').toString();
                }
                Object reprString;
                if (self != value) {
                    reprString = repr.executeObject(value);
                    if (reprString instanceof PString) {
                        reprString = ((PString) reprString).getValue();
                    }
                } else {
                    reprString = "[...]";
                }
                if (reprString instanceof String) {
                    if (initial) {
                        initial = false;
                    } else {
                        result.append(", ");
                    }
                    result.append((String) reprString);
                } else {
                    raise(PythonErrorType.TypeError, "__repr__ returned non-string (type %s)", reprString);
                }
            }
        }
    }

    @Builtin(name = __DELITEM__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {

        @Child private NormalizeIndexNode normalize = NormalizeIndexNode.forList();

        @Specialization(guards = "isIntStorage(primary)")
        protected PNone doPListInt(PList primary, long idx) {
            IntSequenceStorage storage = (IntSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(primary)")
        protected PNone doPListLong(PList primary, long idx) {
            LongSequenceStorage storage = (LongSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isDoubleStorage(primary)")
        protected PNone doPListDouble(PList primary, long idx) {
            DoubleSequenceStorage storage = (DoubleSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isObjectStorage(primary)")
        protected PNone doPListObject(PList primary, long idx) {
            ObjectSequenceStorage storage = (ObjectSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization
        protected PNone doPList(PList list, long idx) {
            SequenceStorage storage = list.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isIntStorage(primary)")
        protected PNone doPListInt(PList primary, PInt idx) {
            IntSequenceStorage storage = (IntSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(primary)")
        protected PNone doPListLong(PList primary, PInt idx) {
            LongSequenceStorage storage = (LongSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isDoubleStorage(primary)")
        protected PNone doPListDouble(PList primary, PInt idx) {
            DoubleSequenceStorage storage = (DoubleSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isObjectStorage(primary)")
        protected PNone doPListObject(PList primary, PInt idx) {
            ObjectSequenceStorage storage = (ObjectSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization
        protected PNone doPList(PList list, PInt idx) {
            SequenceStorage storage = list.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization
        protected PNone doPListSlice(PList self, PSlice slice) {
            self.delSlice(slice);
            return PNone.NONE;
        }

        protected static DelItemNode create() {
            return ListBuiltinsFactory.DelItemNodeFactory.create();
        }

        @Specialization
        protected Object doObjectIndex(PList self, Object objectIdx,
                        @Cached("create()") IndexNode getIndexNode,
                        @Cached("create()") DelItemNode getRecursiveNode) {
            return getRecursiveNode.execute(self, getIndexNode.execute(objectIdx));
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object objectIdx) {
            throw raise(TypeError, "descriptor '__delitem__' requires a 'list' object but received a '%p'", self);
        }
    }

    @Builtin(name = __GETITEM__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization
        protected Object doScalar(PList self, Object key,
                        @Cached("createGetItemNode()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(self.getSequenceStorage(), key);
        }

        protected static SequenceStorageNodes.GetItemNode createGetItemNode() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forList(), (s, f) -> f.createList(s));
        }

        protected static GetItemNode create() {
            return ListBuiltinsFactory.GetItemNodeFactory.create();
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object objectIdx) {
            throw raise(TypeError, "descriptor '__getitem__' requires a 'list' object but received a '%p'", self);
        }
    }

    @Builtin(name = __SETITEM__, fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        @Child private NormalizeIndexNode normalize = NormalizeIndexNode.forListAssign();
        private final ConditionProfile generalizedProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        public PNone doPList(PList primary, PSlice slice, Object value,
                        @Cached("create()") ListNodes.SetSliceNode sliceNode) {
            return sliceNode.execute(primary, slice, value);
        }

        @Specialization
        public Object doPListInt(PList primary, int idx, Object value,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            updateStorage(primary, setItemNode.execute(primary.getSequenceStorage(), idx, value));
            return PNone.NONE;
        }

        @Specialization
        public Object doPListLong(PList primary, long idx, Object value,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            updateStorage(primary, setItemNode.execute(primary.getSequenceStorage(), idx, value));
            return PNone.NONE;
        }

        @Specialization
        public Object doPListPInt(PList primary, PInt idx, Object value,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            updateStorage(primary, setItemNode.execute(primary.getSequenceStorage(), idx, value));
            return PNone.NONE;
        }

        private void updateStorage(PList primary, SequenceStorage newStorage) {
            if (generalizedProfile.profile(primary.getSequenceStorage() != newStorage)) {
                primary.setSequenceStorage(newStorage);
            }
        }

        protected static SetItemNode create() {
            return ListBuiltinsFactory.SetItemNodeFactory.create();
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forListAssign(), () -> ListGeneralizationNode.create());
        }

        @Specialization
        protected Object doObjectIndex(PList self, Object objectIdx, Object value,
                        @Cached("create()") IndexNode getIndexNode,
                        @Cached("create()") SetItemNode getRecursiveNode,
                        @Cached("create()") CastToIndexNode castToIndex) {
            return getRecursiveNode.execute(self, castToIndex.execute(getIndexNode.execute(objectIdx)), value);
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object objectIdx, Object value) {
            throw raise(TypeError, "descriptor '__setitem__' requires a 'list' object but received a '%p'", self);
        }
    }

    // list.append(x)
    @Builtin(name = "append", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListAppendNode extends PythonBinaryBuiltinNode {

        @Specialization
        public PNone appendObjectGeneric(PList list, Object arg,
                        @Cached("createAppend()") SequenceStorageNodes.AppendNode appendNode,
                        @Cached("create()") BranchProfile updateStoreProfile) {
            SequenceStorage newStore = appendNode.execute(list.getSequenceStorage(), arg);
            if (list.getSequenceStorage() != newStore) {
                updateStoreProfile.enter();
                list.setSequenceStorage(newStore);
            }
            return PNone.NONE;
        }

        protected static SequenceStorageNodes.AppendNode createAppend() {
            return SequenceStorageNodes.AppendNode.create(() -> ListGeneralizationNode.create());
        }

        public static ListAppendNode create() {
            return ListAppendNodeFactory.create();
        }
    }

    // list.extend(L)
    @Builtin(name = "extend", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListExtendNode extends PythonBinaryBuiltinNode {

        public abstract PNone execute(PList list, Object source);

        @Specialization
        public PNone extendSequence(PList list, Object iterable,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            updateSequenceStorage(list, extendNode.execute(list.getSequenceStorage(), iterable));
            return PNone.NONE;
        }

        private static void updateSequenceStorage(PList list, SequenceStorage s) {
            if (list.getSequenceStorage() != s) {
                list.setSequenceStorage(s);
            }
        }

        protected static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(() -> ListGeneralizationNode.create());
        }
    }

    // list.insert(i, x)
    @Builtin(name = "insert", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class ListInsertNode extends PythonBuiltinNode {
        protected static final String ERROR_MSG = "'%p' object cannot be interpreted as an integer";

        public abstract PNone execute(PList list, Object index, Object value);

        @Specialization(guards = "isIntStorage(list)")
        public PNone insertIntInt(PList list, int index, int value) {
            IntSequenceStorage target = (IntSequenceStorage) list.getSequenceStorage();
            target.insertIntItem(normalizeIndex(index, list.len()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(list)")
        public PNone insertLongLong(PList list, int index, int value) {
            LongSequenceStorage target = (LongSequenceStorage) list.getSequenceStorage();
            target.insertLongItem(normalizeIndex(index, list.len()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(list)")
        public PNone insertLongLong(PList list, int index, long value) {
            LongSequenceStorage target = (LongSequenceStorage) list.getSequenceStorage();
            target.insertLongItem(normalizeIndex(index, list.len()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isDoubleStorage(list)")
        public PNone insertDoubleDouble(PList list, int index, double value) {
            DoubleSequenceStorage target = (DoubleSequenceStorage) list.getSequenceStorage();
            target.insertDoubleItem(normalizeIndex(index, list.len()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNotSpecialCase(list, value)")
        public PNone insert(PList list, int index, Object value) {
            list.insert(normalizeIndex(index, list.len()), value);
            return PNone.NONE;
        }

        @Specialization
        public PNone insertLongIndex(PList list, long index, Object value,
                        @Cached("createListInsertNode()") ListInsertNode insertNode) {
            int where = index < Integer.MIN_VALUE ? 0 : index > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) index;
            where = normalizeIndex(where, list.len());
            return insertNode.execute(list, where, value);
        }

        @Specialization
        public PNone insertPIntIndex(PList list, PInt index, Object value,
                        @Cached("createListInsertNode()") ListInsertNode insertNode) {
            int where = normalizePIntForIndex(index);
            where = normalizeIndex(where, list.len());
            return insertNode.execute(list, where, value);
        }

        @Specialization(guards = {"!isIntegerOrPInt(i)"})
        public PNone insert(PList list, Object i, Object value,
                        @Cached("createInteger(ERROR_MSG)") IndexNode indexNode,
                        @Cached("createListInsertNode()") ListInsertNode insertNode) {
            Object indexValue = indexNode.execute(i);
            return insertNode.execute(list, indexValue, value);
        }

        @TruffleBoundary
        private static int normalizePIntForIndex(PInt index) {
            int where = 0;
            BigInteger bigIndex = index.getValue();
            if (bigIndex.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) == -1) {
                where = 0;
            } else if (bigIndex.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) == 1) {
                where = Integer.MAX_VALUE;
            } else {
                where = bigIndex.intValue();
            }
            return where;
        }

        private static int normalizeIndex(int index, int len) {
            int idx = index;
            if (idx < 0) {
                idx += len;
                if (idx < 0) {
                    idx = 0;
                }
            }
            if (idx > len) {
                idx = len;
            }
            return idx;
        }

        protected boolean isNotSpecialCase(PList list, Object value) {
            return !((PGuards.isIntStorage(list) && value instanceof Integer) || (PGuards.isLongStorage(list) && PGuards.isInteger(value)) ||
                            (PGuards.isDoubleStorage(list) && value instanceof Double));
        }

        protected boolean isIntegerOrPInt(Object index) {
            return index instanceof Integer || index instanceof PInt;
        }

        protected ListInsertNode createListInsertNode() {
            return ListBuiltinsFactory.ListInsertNodeFactory.create(new PNode[0]);
        }

    }

    // list.remove(x)
    @Builtin(name = "remove", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListRemoveNode extends PythonBuiltinNode {

        private static String NOT_IN_LIST_MESSAGE = "list.index(x): x not in list";

        @Specialization(guards = "isIntStorage(list)")
        public PNone removeInt(PList list, int value) {
            IntSequenceStorage store = (IntSequenceStorage) list.getSequenceStorage();
            for (int index = 0; index < store.length(); index++) {
                if (value == store.getIntItemNormalized(index)) {
                    store.delItemInBound(index);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, NOT_IN_LIST_MESSAGE);
        }

        @Specialization(guards = "isLongStorage(list)")
        public PNone removeLong(PList list, int value) {
            LongSequenceStorage store = (LongSequenceStorage) list.getSequenceStorage();
            for (int index = 0; index < store.length(); index++) {
                if (value == store.getLongItemNormalized(index)) {
                    store.delItemInBound(index);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, NOT_IN_LIST_MESSAGE);
        }

        @Specialization(guards = "isLongStorage(list)")
        public PNone removeLong(PList list, long value) {
            LongSequenceStorage store = (LongSequenceStorage) list.getSequenceStorage();
            for (int index = 0; index < store.length(); index++) {
                if (value == store.getLongItemNormalized(index)) {
                    store.delItemInBound(index);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, NOT_IN_LIST_MESSAGE);
        }

        @Specialization(guards = "isDoubleStorage(list)")
        public PNone removeDouble(PList list, double value) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) list.getSequenceStorage();
            for (int index = 0; index < store.length(); index++) {
                if (value == store.getDoubleItemNormalized(index)) {
                    store.delItemInBound(index);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, NOT_IN_LIST_MESSAGE);
        }

        @Specialization(guards = "isNotSpecialCase(list, value)")
        public PNone remove(PList list, Object value,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            int len = list.len();
            SequenceStorage listStore = list.getSequenceStorage();
            for (int i = 0; i < len; i++) {
                Object object = getItemNode.execute(listStore, i);
                if (eqNode.executeBool(object, value)) {
                    listStore.delItemInBound(i);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, NOT_IN_LIST_MESSAGE);
        }

        protected boolean isNotSpecialCase(PList list, Object value) {
            return !((PGuards.isIntStorage(list) && value instanceof Integer) || (PGuards.isLongStorage(list) && (value instanceof Integer || value instanceof Long)) ||
                            PGuards.isDoubleStorage(list) && value instanceof Double);
        }
    }

    // list.pop([i])
    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListPopNode extends PythonBuiltinNode {

        @Child private SequenceStorageNodes.GetItemNode getItemNode;

        @CompilationFinal private ValueProfile storeProfile;

        @Specialization(guards = "isIntStorage(list)")
        public int popInt(PList list, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile isEmpty) {
            raiseIndexError(isEmpty.profile(list.len() == 0));
            IntSequenceStorage store = (IntSequenceStorage) list.getSequenceStorage();
            return store.popInt();
        }

        @Specialization(guards = "isLongStorage(list)")
        public long popLong(PList list, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile isEmpty) {
            raiseIndexError(isEmpty.profile(list.len() == 0));
            LongSequenceStorage store = (LongSequenceStorage) list.getSequenceStorage();
            return store.popLong();
        }

        @Specialization(guards = "isDoubleStorage(list)")
        public double popDouble(PList list, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile isEmpty) {
            raiseIndexError(isEmpty.profile(list.len() == 0));
            DoubleSequenceStorage store = (DoubleSequenceStorage) list.getSequenceStorage();
            return store.popDouble();
        }

        @Specialization(guards = "isObjectStorage(list)")
        public Object popObject(PList list, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile isEmpty) {
            raiseIndexError(isEmpty.profile(list.len() == 0));
            ObjectSequenceStorage store = (ObjectSequenceStorage) list.getSequenceStorage();
            return store.popObject();
        }

        @Specialization
        public Object popLast(PList list, @SuppressWarnings("unused") PNone none) {
            SequenceStorage store = getStoreProfile().profile(list.getSequenceStorage());
            int len = store.length();
            Object ret = getGetItemNode().execute(store, -1);
            store.delItemInBound(len - 1);
            return ret;
        }

        @Specialization
        public Object pop(PList list, boolean bindex,
                        @Cached("createBinaryProfile()") ConditionProfile isOutOfRange) {
            int index = bindex ? 1 : 0;
            return popOnIndex(list.getSequenceStorage(), index, isOutOfRange);
        }

        @Specialization
        public Object pop(PList list, int index,
                        @Cached("createBinaryProfile()") ConditionProfile isOutOfRange) {
            return popOnIndex(list.getSequenceStorage(), index, isOutOfRange);
        }

        @Specialization
        @SuppressWarnings("unused")
        public Object pop(PList list, long arg) {
            raiseIndexError(true);
            return null;
        }

        @Specialization
        @SuppressWarnings("unused")
        public Object pop(PList list, Object arg) {
            throw raise(TypeError, "integer argument expected, got %p", arg);
        }

        protected void raiseIndexError(boolean con) {
            if (con) {
                throw raise(PythonErrorType.IndexError, "pop index out of range");
            }
        }

        private Object popOnIndex(SequenceStorage store, int index, ConditionProfile cp) {

            SequenceStorage profiled = getStoreProfile().profile(store);
            int len = profiled.length();
            if (cp.profile((index < 0 && (index + len) < 0) || index >= len)) {
                throw raise(PythonErrorType.IndexError, "pop index out of range");
            }
            Object ret = getGetItemNode().execute(profiled, index);
            // this is safe because index is already verified by 'GetItemNode'
            profiled.delItemInBound(index);
            return ret;
        }

        private ValueProfile getStoreProfile() {
            if (storeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storeProfile = ValueProfile.createClassProfile();
            }
            return storeProfile;
        }

        private SequenceStorageNodes.GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.create("pop index out of range")));
            }
            return getItemNode;
        }
    }

    // list.index(x)
    @Builtin(name = "index", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class ListIndexNode extends PythonBuiltinNode {
        protected final static String ERROR_TYPE_MESSAGE = "slice indices must be integers or have an __index__ method";

        @Child private SequenceStorageNodes.GetItemNode getItemNode;

        public abstract int execute(Object arg1, Object arg2, Object arg3, Object arg4);

        private static int correctIndex(PList list, long index) {
            long resultIndex = index;
            if (resultIndex < 0) {
                resultIndex += list.len();
                if (resultIndex < 0) {
                    return 0;
                }
            }
            return (int) Math.min(resultIndex, Integer.MAX_VALUE);
        }

        @TruffleBoundary
        private static int correctIndex(PList list, PInt index) {
            BigInteger value = index.getValue();
            if (value.compareTo(BigInteger.ZERO) < 0) {
                BigInteger resultAdd = value.add(BigInteger.valueOf(list.len()));
                if (resultAdd.compareTo(BigInteger.ZERO) < 0) {
                    return 0;
                }
                return resultAdd.intValue();
            }
            return value.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        }

        private int findIndex(PList list, Object value, int start, int end, BinaryComparisonNode eqNode) {
            for (int i = start; i < end && i < list.len(); i++) {
                Object object = getGetItemNode().execute(list.getSequenceStorage(), i);
                if (eqNode.executeBool(object, value)) {
                    return i;
                }
            }
            throw raise(PythonErrorType.ValueError, "x not in list");
        }

        @Specialization
        int index(PList self, Object value, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, 0, self.len(), eqNode);
        }

        @Specialization
        int index(PList self, Object value, long start, @SuppressWarnings("unused") PNone end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), self.len(), eqNode);
        }

        @Specialization
        int index(PList self, Object value, long start, long end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), correctIndex(self, end), eqNode);
        }

        @Specialization
        int indexPI(PList self, Object value, PInt start, @SuppressWarnings("unused") PNone end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), self.len(), eqNode);
        }

        @Specialization
        int indexPIPI(PList self, Object value, PInt start, PInt end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), correctIndex(self, end), eqNode);
        }

        @Specialization
        int indexLPI(PList self, Object value, long start, PInt end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), correctIndex(self, end), eqNode);
        }

        @Specialization
        int indexPIL(PList self, Object value, PInt start, Long end,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return findIndex(self, value, correctIndex(self, start), correctIndex(self, end), eqNode);
        }

        @Specialization
        @SuppressWarnings("unused")
        int indexDO(PTuple self, Object value, double start, Object end) {
            throw raise(TypeError, ERROR_TYPE_MESSAGE);
        }

        @Specialization
        @SuppressWarnings("unused")
        int indexOD(PTuple self, Object value, Object start, double end) {
            throw raise(TypeError, ERROR_TYPE_MESSAGE);
        }

        @Specialization(guards = "!isNumber(start)")
        int indexO(PTuple self, Object value, Object start, PNone end,
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode startNode,
                        @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object startValue = startNode.execute(start);
            return indexNode.execute(self, value, startValue, end);
        }

        @Specialization(guards = {"!isNumber(end)",})
        int indexLO(PTuple self, Object value, long start, Object end,
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode endNode,
                        @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object endValue = endNode.execute(end);
            return indexNode.execute(self, value, start, endValue);
        }

        @Specialization(guards = {"!isNumber(start) || !isNumber(end)",})
        int indexOO(PTuple self, Object value, Object start, Object end,
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode startNode,
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode endNode,
                        @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object startValue = startNode.execute(start);
            Object endValue = endNode.execute(end);
            return indexNode.execute(self, value, startValue, endValue);
        }

        protected ListIndexNode createIndexNode() {
            return ListBuiltinsFactory.ListIndexNodeFactory.create(new PNode[0]);
        }

        private SequenceStorageNodes.GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getItemNode;
        }
    }

    // list.count(x)
    @Builtin(name = "count", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListCountNode extends PythonBuiltinNode {

        @Specialization
        long count(PList self, Object value,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            long count = 0;
            for (int i = 0; i < self.len(); i++) {
                Object object = getItemNode.execute(self.getSequenceStorage(), i);
                if (eqNode.executeBool(object, value)) {
                    count++;
                }
            }
            return count;
        }

    }

    // list.clear()
    @Builtin(name = "clear", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ListClearNode extends PythonBuiltinNode {

        @Specialization
        public PNone clear(PList list) {
            if (list.len() > 0) {
                list.setSequenceStorage(new EmptySequenceStorage());
            }
            return PNone.NONE;
        }

    }

    // list.reverse()
    @Builtin(name = "reverse", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ListReverseNode extends PythonUnaryBuiltinNode {

        @Specialization
        PList reverse(PList list) {
            list.reverse();
            return list;
        }

        public static ListReverseNode create() {
            return ListReverseNodeFactory.create();
        }
    }

    @Builtin(name = __LEN__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {

        @Specialization
        int doGeneric(PList list,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(list.getSequenceStorage());
        }
    }

    @Builtin(name = __ADD__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        PList doPList(PList left, PList other,
                        @Cached("createConcat()") SequenceStorageNodes.ConcatNode concatNode) {
            SequenceStorage newStore = concatNode.execute(left.getSequenceStorage(), other.getSequenceStorage());
            return factory().createList(left.getPythonClass(), newStore);
        }

        @Specialization(guards = "!isList(right)")
        Object doGeneric(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, "can only concatenate list (not \"%p\") to list", right);
        }

        protected static SequenceStorageNodes.ConcatNode createConcat() {
            return SequenceStorageNodes.ConcatNode.create(() -> SequenceStorageNodes.ListGeneralizationNode.create());
        }
    }

    @Builtin(name = __IADD__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        PList extendSequence(PList list, Object iterable,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            updateSequenceStorage(list, extendNode.execute(list.getSequenceStorage(), iterable));
            return list;
        }

        private static void updateSequenceStorage(PList list, SequenceStorage s) {
            if (list.getSequenceStorage() != s) {
                list.setSequenceStorage(s);
            }
        }

        protected static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(() -> ListGeneralizationNode.create());
        }
    }

    @Builtin(name = __MUL__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {

        @Specialization
        PList doPListInt(PList left, Object right,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode) {
            try {
                SequenceStorage repeated = repeatNode.execute(left.getSequenceStorage(), right);
                return factory().createList(repeated);
            } catch (ArithmeticException | OutOfMemoryError e) {
                throw raise(MemoryError);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __IMUL__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IMulNode extends PythonBuiltinNode {

        public abstract PList execute(PList list, Object value);

        @Specialization
        Object doGeneric(PList list, Object right,
                        @Cached("createBinaryProfile()") ConditionProfile updatedProfile,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode) {

            SequenceStorage store = list.getSequenceStorage();
            SequenceStorage updated = repeatNode.execute(store, right);
            if (updatedProfile.profile(store != updated)) {
                list.setSequenceStorage(updated);
            }
            return list;
        }

        protected IMulNode createIMulNode() {
            return ListBuiltinsFactory.IMulNodeFactory.create(new PNode[0]);
        }
    }

    @Builtin(name = __RMUL__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __EQ__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(PList left, PList right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        boolean doOther(Object left, Object right) {
            return false;
        }
    }

    @Builtin(name = __NE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(PList left, PList right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode eqNode) {
            return !eqNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        boolean doOther(Object left, Object right) {
            return true;
        }
    }

    @Builtin(name = __GE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(PList left, PList right,
                        @Cached("createGe()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = __LE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(PList left, PList right,
                        @Cached("createLe()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = __GT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(PList left, PList right,
                        @Cached("createGt()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        PNotImplemented doOther(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doPList(PList left, PList right,
                        @Cached("createLt()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        PNotImplemented contains(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __CONTAINS__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(PSequence self, Object other,
                        @Cached("create()") SequenceStorageNodes.ContainsNode containsNode) {
            return containsNode.execute(self.getSequenceStorage(), other);
        }
    }

    @Builtin(name = __BOOL__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonBuiltinNode {
        @Specialization(guards = "isEmptyStorage(list)")
        public boolean doPListEmpty(@SuppressWarnings("unused") PList list) {
            return false;
        }

        @Specialization(guards = "isIntStorage(primary)")
        public boolean doPListInt(PList primary) {
            IntSequenceStorage store = (IntSequenceStorage) primary.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization(guards = "isDoubleStorage(primary)")
        public boolean doPListDouble(PList primary) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) primary.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization(guards = "isObjectStorage(primary)")
        public boolean doPListObject(PList primary) {
            ObjectSequenceStorage store = (ObjectSequenceStorage) primary.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization
        boolean doPList(PList operand) {
            return operand.len() != 0;
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ITER__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"isIntStorage(primary)"})
        public PIntegerSequenceIterator doPListInt(PList primary) {
            return factory().createIntegerSequenceIterator((IntSequenceStorage) primary.getSequenceStorage());
        }

        @Specialization(guards = {"isLongStorage(primary)"})
        public PLongSequenceIterator doPListLong(PList primary) {
            return factory().createLongSequenceIterator((LongSequenceStorage) primary.getSequenceStorage());
        }

        @Specialization(guards = {"isDoubleStorage(primary)"})
        public PDoubleSequenceIterator doPListDouble(PList primary) {
            return factory().createDoubleSequenceIterator((DoubleSequenceStorage) primary.getSequenceStorage());
        }

        @Specialization(guards = {"!isIntStorage(primary)", "!isLongStorage(primary)", "!isDoubleStorage(primary)"})
        public PSequenceIterator doPList(PList primary) {
            return factory().createSequenceIterator(primary);
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = __HASH__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonBuiltinNode {
        @Specialization
        Object doGeneric(Object self) {
            throw raise(TypeError, "unhashable type: '%p'", self);
        }
    }
}
